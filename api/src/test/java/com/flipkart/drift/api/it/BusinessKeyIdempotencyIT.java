package com.flipkart.drift.api.it;

import com.codahale.metrics.MetricRegistry;
import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.config.IdempotencyConfig;
import com.flipkart.drift.api.filters.IdempotencyFilter;
import com.flipkart.drift.api.filters.RequestThreadContext;
import com.flipkart.drift.api.filters.ResponseFilter;
import com.flipkart.drift.api.service.RedisPubSubService;
import com.flipkart.drift.api.service.builder.WorkflowDefinitionService;
import com.flipkart.drift.api.service.TemporalService;
import com.flipkart.drift.api.service.idempotency.IdempotencyMetrics;
import com.flipkart.drift.api.service.utils.IdempotencyKeyResolver;
import com.flipkart.drift.api.service.utils.Utility;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.response.WorkflowResponse;
import com.flipkart.drift.workflows.GenericWorkflow;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end idempotency round trip driven through the real collaborators
 * (IdempotencyKeyResolver -> IdempotencyFilter -> TemporalService -> ResponseFilter) against
 * Temporal's in-memory {@link TestWorkflowEnvironment}.
 * <p>
 * Deviation from a literal HTTP-layer test: this repo has no jersey-test-framework/grizzly
 * dependency wired anywhere (verified across all poms), so rather than adding new test
 * infrastructure this test drives the same production filter/service objects directly with
 * mocked JAX-RS context interfaces (the framework boundary, not an internal class) --
 * functionally equivalent coverage of the request -> filter -> TemporalService -> Temporal
 * round trip without a live HTTP server.
 */
class BusinessKeyIdempotencyIT {

    private static final String HEADER = "X-Drift-Idempotency-Key";
    private static TestWorkflowEnvironment testEnv;
    private static Worker worker;

    private IdempotencyFilter filter;
    private TemporalService temporalService;
    private ResponseFilter responseFilter;

    @BeforeAll
    static void startTemporalTestEnvironment() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("business-key-idempotency-it-queue");
        worker.registerWorkflowImplementationTypes(FakeGenericWorkflowImpl.class);
        testEnv.start();
    }

    @AfterAll
    static void stopTemporalTestEnvironment() {
        testEnv.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        DriftConfiguration configuration = new DriftConfiguration();
        configuration.setTemporalFrontEnd("localhost:7233");
        configuration.setTemporalTaskQueue("business-key-idempotency-it-queue");
        IdempotencyConfig idempotencyConfig = new IdempotencyConfig();
        idempotencyConfig.setHeaders(List.of(HEADER));
        idempotencyConfig.setOptional(true);
        configuration.setIdempotencyConfig(idempotencyConfig);

        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(idempotencyConfig);
        IdempotencyMetrics metrics = new IdempotencyMetrics(new MetricRegistry());
        filter = new IdempotencyFilter(resolver, idempotencyConfig);
        responseFilter = new ResponseFilter();

        RedisPubSubService redisPubSubService = mock(RedisPubSubService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.concurrent.Callable<Void> action = invocation.getArgument(1);
            action.call();
            return null;
        }).when(redisPubSubService).subscribeAndExecute(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        temporalService = new TemporalService(redisPubSubService, configuration, new Utility(), metrics,
                mock(WorkflowDefinitionService.class));
        Field clientField = TemporalService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(temporalService, testEnv.getWorkflowClient());

        RequestThreadContext.get().clear();
    }

    @AfterEach
    void tearDown() {
        RequestThreadContext.remove();
    }

    private void resolveIdempotencyHeader(String tenant, String clientId, String rawKey) {
        RequestThreadContext.get().setTenant(tenant);
        RequestThreadContext.get().setClientId(clientId);

        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("v3/workflow/start");
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        MultivaluedMap<String, String> headers = new MultivaluedStringMap();
        headers.add(HEADER, rawKey);
        when(ctx.getHeaders()).thenReturn(headers);

        filter.filter(ctx);
    }

    private WorkflowResponse startViaTemporalService(boolean shouldFail) {
        WorkflowStartRequest request = new WorkflowStartRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("shouldFail", shouldFail);
        request.setParams(params);
        return temporalService.startWorkflow(request);
    }

    private Map<String, List<Object>> responseHeadersFor() {
        ContainerRequestContext requestCtx = mock(ContainerRequestContext.class);
        when(requestCtx.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        ContainerResponseContext responseCtx = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(responseCtx.getHeaders()).thenReturn(headers);
        responseFilter.filter(requestCtx, responseCtx);
        return headers;
    }

    @Test
    void duplicateRequestsResolveToSameWorkflowIdAndOneExecution() {
        resolveIdempotencyHeader("tenant1", "client1", "order-dup-1");
        WorkflowResponse first = startViaTemporalService(false);
        boolean firstReplay = RequestThreadContext.get().isResolvedFromExistingWorkflow();

        // Second POST ~immediately after (simulating "~100ms apart"), same key
        resolveIdempotencyHeader("tenant1", "client1", "order-dup-1");
        WorkflowResponse second = startViaTemporalService(false);
        boolean secondReplay = RequestThreadContext.get().isResolvedFromExistingWorkflow();

        assertEquals(first.getWorkflowId(), second.getWorkflowId());
        assertFalse(firstReplay);
        assertTrue(secondReplay);
    }

    @Test
    void sameKeyDifferentClientIdsProduceDistinctWorkflowIds() {
        resolveIdempotencyHeader("tenant1", "client1", "order-shared-key");
        WorkflowResponse first = startViaTemporalService(false);

        resolveIdempotencyHeader("tenant1", "client2", "order-shared-key");
        WorkflowResponse second = startViaTemporalService(false);

        assertNotEquals(first.getWorkflowId(), second.getWorkflowId());
    }

    @Test
    void sameKeyDifferentTenantsProduceDistinctWorkflowIds() {
        resolveIdempotencyHeader("tenant1", "client1", "order-shared-key-2");
        WorkflowResponse first = startViaTemporalService(false);

        resolveIdempotencyHeader("tenant2", "client1", "order-shared-key-2");
        WorkflowResponse second = startViaTemporalService(false);

        assertNotEquals(first.getWorkflowId(), second.getWorkflowId());
    }

    @Test
    void retryAfterFailedRunSucceedsAndReRuns() {
        resolveIdempotencyHeader("tenant1", "client1", "order-retry-1");
        WorkflowResponse failedRun = startViaTemporalService(true);
        assertEquals(WorkflowStatus.FAILED, failedRun.getWorkflowStatus());

        resolveIdempotencyHeader("tenant1", "client1", "order-retry-1");
        WorkflowResponse retried = startViaTemporalService(false);

        assertEquals(failedRun.getWorkflowId(), retried.getWorkflowId());
        assertEquals(WorkflowStatus.COMPLETED, retried.getWorkflowStatus());
    }

    @Test
    void legacyRequestWithoutHeaderKeepsAutoGeneratedIdAndTerminateIfRunning() {
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");
        // no idempotency header resolved -- filter never invoked, legacy path

        WorkflowStartRequest request = new WorkflowStartRequest();
        WorkflowResponse response = temporalService.startWorkflow(request);

        assertTrue(response.getWorkflowId().startsWith("WF-"));
        assertNull(RequestThreadContext.get().getResolvedWorkflowId());
    }

    @Test
    void responseHeadersEndToEndForFreshDuplicateAndLegacyRequests() {
        resolveIdempotencyHeader("tenant1", "client1", "order-headers-1");
        startViaTemporalService(false);
        Map<String, List<Object>> freshHeaders = responseHeadersFor();
        assertTrue(freshHeaders.containsKey(ResponseFilter.WORKFLOW_ID_HEADER));
        assertFalse(freshHeaders.containsKey(ResponseFilter.IDEMPOTENT_REPLAY_HEADER));

        resolveIdempotencyHeader("tenant1", "client1", "order-headers-1");
        startViaTemporalService(false);
        Map<String, List<Object>> replayHeaders = responseHeadersFor();
        assertTrue(replayHeaders.containsKey(ResponseFilter.WORKFLOW_ID_HEADER));
        assertEquals(freshHeaders.get(ResponseFilter.WORKFLOW_ID_HEADER), replayHeaders.get(ResponseFilter.WORKFLOW_ID_HEADER));
        assertTrue(replayHeaders.containsKey(ResponseFilter.IDEMPOTENT_REPLAY_HEADER));

        RequestThreadContext.get().clear();
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");
        temporalService.startWorkflow(new WorkflowStartRequest());
        Map<String, List<Object>> legacyHeaders = responseHeadersFor();
        assertFalse(legacyHeaders.containsKey(ResponseFilter.WORKFLOW_ID_HEADER));
        assertFalse(legacyHeaders.containsKey(ResponseFilter.IDEMPOTENT_REPLAY_HEADER));
    }
}
