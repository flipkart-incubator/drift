package com.flipkart.drift.api.service;

import com.codahale.metrics.MetricRegistry;
import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.config.IdempotencyConfig;
import com.flipkart.drift.api.filters.RequestThreadContext;
import com.flipkart.drift.api.service.idempotency.IdempotencyMetrics;
import com.flipkart.drift.api.service.utils.Utility;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.response.WorkflowResponse;
import com.flipkart.drift.workflows.GenericWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mocks only the Temporal client (system boundary, per TEST.md) via reflection field
 * injection -- TemporalService's constructor builds its WorkflowClient internally rather
 * than accepting one, so a mock is swapped in post-construction rather than via DI. This
 * avoids a larger constructor-injection refactor outside subtask-6's declared scope while
 * still keeping the mock at the correct boundary (RequestThreadContext is NOT mocked).
 */
class TemporalServiceTest {

    private TemporalService temporalService;
    private WorkflowClient mockClient;
    private RedisPubSubService redisPubSubService;

    @BeforeEach
    void setUp() throws Exception {
        DriftConfiguration configuration = new DriftConfiguration();
        configuration.setTemporalFrontEnd("localhost:7233");
        configuration.setTemporalTaskQueue("test-queue");
        IdempotencyConfig idempotencyConfig = new IdempotencyConfig();
        configuration.setIdempotencyConfig(idempotencyConfig);

        redisPubSubService = mock(RedisPubSubService.class);
        Utility utility = new Utility();
        IdempotencyMetrics idempotencyMetrics = new IdempotencyMetrics(new MetricRegistry());

        temporalService = new TemporalService(redisPubSubService, configuration, utility, idempotencyMetrics);

        mockClient = mock(WorkflowClient.class);
        Field clientField = TemporalService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(temporalService, mockClient);

        RequestThreadContext.get().clear();
    }

    @AfterEach
    void tearDown() {
        RequestThreadContext.remove();
    }

    private WorkflowState stateFor(String workflowId) {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId(workflowId);
        state.setDisposition("SUCCESS");
        return state;
    }

    @Test
    void nonIdempotentRequestUsesTerminateIfRunningReusePolicy() {
        GenericWorkflow workflowStub = mock(GenericWorkflow.class);
        when(mockClient.newWorkflowStub(org.mockito.ArgumentMatchers.eq(GenericWorkflow.class),
                any(WorkflowOptions.class))).thenAnswer(invocation -> {
            WorkflowOptions options = invocation.getArgument(1);
            assertEquals(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING,
                    options.getWorkflowIdReusePolicy());
            return workflowStub;
        });
        when(workflowStub.getWorkflowState()).thenReturn(stateFor("WF-legacy-1"));

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setWorkflowId("WF-legacy-1");

        WorkflowResponse response = temporalService.executeWorkflow(request);

        assertEquals("WF-legacy-1", response.getWorkflowId());
        assertFalse(RequestThreadContext.get().isResolvedFromExistingWorkflow());
    }

    @Test
    void idempotentRequestUsesAllowDuplicateFailedOnlyReusePolicy() {
        RequestThreadContext.get().setResolvedWorkflowId("WF-tenant1-client1-abc");
        RequestThreadContext.get().setTenant("tenant1");

        GenericWorkflow workflowStub = mock(GenericWorkflow.class);
        when(mockClient.newWorkflowStub(org.mockito.ArgumentMatchers.eq(GenericWorkflow.class),
                any(WorkflowOptions.class))).thenAnswer(invocation -> {
            WorkflowOptions options = invocation.getArgument(1);
            assertEquals(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY,
                    options.getWorkflowIdReusePolicy());
            return workflowStub;
        });
        when(workflowStub.getWorkflowState()).thenReturn(stateFor("WF-tenant1-client1-abc"));

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setWorkflowId("WF-tenant1-client1-abc");

        WorkflowResponse response = temporalService.executeWorkflow(request);

        assertEquals("WF-tenant1-client1-abc", response.getWorkflowId());
    }

    @Test
    void alreadyStartedTranslatesToExistingStateFetch() {
        RequestThreadContext.get().setResolvedWorkflowId("WF-tenant1-client1-dup");
        RequestThreadContext.get().setTenant("tenant1");

        WorkflowExecution execution = WorkflowExecution.newBuilder().setWorkflowId("WF-tenant1-client1-dup").build();
        WorkflowExecutionAlreadyStarted alreadyStarted =
                new WorkflowExecutionAlreadyStarted(execution, "GenericWorkflow", null);

        GenericWorkflow startingStub = mock(GenericWorkflow.class);
        GenericWorkflow existingStub = mock(GenericWorkflow.class);

        when(mockClient.newWorkflowStub(org.mockito.ArgumentMatchers.eq(GenericWorkflow.class),
                any(WorkflowOptions.class))).thenReturn(startingStub);
        when(mockClient.newWorkflowStub(GenericWorkflow.class, "WF-tenant1-client1-dup")).thenReturn(existingStub);
        when(existingStub.getWorkflowState()).thenReturn(stateFor("WF-tenant1-client1-dup"));

        doThrowFromSubscribeAndExecute(alreadyStarted);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setWorkflowId("WF-tenant1-client1-dup");

        WorkflowResponse response = temporalService.executeWorkflow(request);

        assertEquals("WF-tenant1-client1-dup", response.getWorkflowId());
        assertTrue(RequestThreadContext.get().isResolvedFromExistingWorkflow());
    }

    @SuppressWarnings("unchecked")
    private void doThrowFromSubscribeAndExecute(Exception toThrow) {
        try {
            org.mockito.Mockito.doAnswer(invocation -> {
                throw toThrow;
            }).when(redisPubSubService).subscribeAndExecute(any(), any(), any());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
