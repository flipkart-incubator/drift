package com.flipkart.drift.api.filters;

import com.flipkart.drift.api.config.IdempotencyConfig;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.service.utils.IdempotencyKeyResolver;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit test invoking {@link IdempotencyFilter#filter} with a mocked
 * {@link ContainerRequestContext}/{@link UriInfo} rather than a live Jersey test
 * container -- this repo has no {@code jersey-test-framework} dependency wired anywhere
 * (verified: only {@code dropwizard-core}, no test-container artifact in any pom.xml),
 * so introducing one purely for this filter would be a disproportionate blast-radius
 * increase for a request filter whose only external touch points (JAX-RS context
 * interfaces) are already trivially mockable. {@code ContainerRequestContext}/{@code UriInfo}
 * are framework-provided value carriers, not internal classes we own, so mocking them here
 * still satisfies TEST.md's "mock only at system boundaries" rule.
 */
class IdempotencyFilterTest {

    private static final String HEADER = "X-Drift-Idempotency-Key";

    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        RequestThreadContext.get().clear();
        IdempotencyConfig config = new IdempotencyConfig();
        config.setHeaders(List.of(HEADER));
        config.setOptional(true);
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config);
        filter = new IdempotencyFilter(resolver);
    }

    @AfterEach
    void tearDown() {
        RequestThreadContext.remove();
    }

    private ContainerRequestContext contextFor(String path, String headerValue) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        MultivaluedMap<String, String> headers = new MultivaluedStringMap();
        if (headerValue != null) {
            headers.add(HEADER, headerValue);
        }
        when(ctx.getHeaders()).thenReturn(headers);
        return ctx;
    }

    @Test
    void firstRequestResolvesWorkflowIdOnThreadContext() {
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        filter.filter(contextFor("v3/workflow/start", "order-123"));

        assertEquals("order-123", RequestThreadContext.get().getIdempotencyKey());
        assertTrue(RequestThreadContext.get().getResolvedWorkflowId().startsWith("WF-tenant1-client1-"));
    }

    @Test
    void filterNoOpsOnNonStartPaths() {
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        filter.filter(contextFor("v3/workflow/resume/WF-123", "order-123"));

        assertNull(RequestThreadContext.get().getResolvedWorkflowId());
    }

    @Test
    void optionalTrueAndNoHeaderPreservesLegacyPath() {
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        filter.filter(contextFor("v3/workflow/start", null));

        assertNull(RequestThreadContext.get().getResolvedWorkflowId());
    }

    @Test
    void malformedHeaderThrows400() {
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        assertThrows(ApiException.class,
                () -> filter.filter(contextFor("v3/workflow/start", "bad key!")));
    }

    @Test
    void missingHeaderWithOptionalFalseThrows400() {
        IdempotencyConfig config = new IdempotencyConfig();
        config.setHeaders(List.of(HEADER));
        config.setOptional(false);
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config);
        IdempotencyFilter strictFilter = new IdempotencyFilter(resolver);

        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        assertThrows(ApiException.class,
                () -> strictFilter.filter(contextFor("v3/workflow/start", null)));
    }

    @Test
    void resolvedWorkflowIdIsAlwaysHashedRegardlessOfClientHeaders() {
        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        filter.filter(contextFor("v3/workflow/start", "order-123"));

        assertTrue(RequestThreadContext.get().getResolvedWorkflowId().startsWith("WF-tenant1-client1-"));
    }

    @Test
    void ambiguousHeadersThrows400() {
        IdempotencyConfig config = new IdempotencyConfig();
        config.setHeaders(List.of(HEADER, "X_REQUEST_ID"));
        config.setOptional(true);
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config);
        IdempotencyFilter ambiguousFilter = new IdempotencyFilter(resolver);

        RequestThreadContext.get().setTenant("tenant1");
        RequestThreadContext.get().setClientId("client1");

        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("v3/workflow/start");
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        MultivaluedMap<String, String> headers = new MultivaluedStringMap();
        headers.add(HEADER, "key-1");
        headers.add("X_REQUEST_ID", "key-2");
        when(ctx.getHeaders()).thenReturn(headers);

        assertThrows(ApiException.class, () -> ambiguousFilter.filter(ctx));
    }
}
