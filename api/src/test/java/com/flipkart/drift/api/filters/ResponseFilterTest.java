package com.flipkart.drift.api.filters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseFilterTest {

    private final ResponseFilter responseFilter = new ResponseFilter();

    @AfterEach
    void tearDown() {
        RequestThreadContext.remove();
    }

    private ContainerResponseContext respond() {
        ContainerResponseContext ctx = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(ctx.getHeaders()).thenReturn(headers);
        return ctx;
    }

    private ContainerRequestContext request() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaders()).thenReturn(new javax.ws.rs.core.MultivaluedHashMap<>());
        return ctx;
    }

    @Test
    void workflowIdHeaderPresentWhenResolved() {
        RequestThreadContext.get().setResolvedWorkflowId("WF-tenant1-client1-abc");
        RequestThreadContext.get().setResolvedFromExistingWorkflow(false);

        ContainerResponseContext response = respond();
        responseFilter.filter(request(), response);

        assertTrue(response.getHeaders().containsKey(ResponseFilter.WORKFLOW_ID_HEADER));
        assertFalse(response.getHeaders().containsKey(ResponseFilter.IDEMPOTENT_REPLAY_HEADER));
    }

    @Test
    void idempotentReplayHeaderPresentOnlyWhenResolvedFromExistingWorkflow() {
        RequestThreadContext.get().setResolvedWorkflowId("WF-tenant1-client1-abc");
        RequestThreadContext.get().setResolvedFromExistingWorkflow(true);

        ContainerResponseContext response = respond();
        responseFilter.filter(request(), response);

        assertTrue(response.getHeaders().containsKey(ResponseFilter.WORKFLOW_ID_HEADER));
        assertTrue(response.getHeaders().containsKey(ResponseFilter.IDEMPOTENT_REPLAY_HEADER));
        assertTrue(response.getHeaders().getFirst(ResponseFilter.IDEMPOTENT_REPLAY_HEADER).toString().equals("true"));
    }

    @Test
    void neitherHeaderPresentForLegacyRequest() {
        RequestThreadContext.get().clear();

        ContainerResponseContext response = respond();
        responseFilter.filter(request(), response);

        assertFalse(response.getHeaders().containsKey(ResponseFilter.WORKFLOW_ID_HEADER));
        assertFalse(response.getHeaders().containsKey(ResponseFilter.IDEMPOTENT_REPLAY_HEADER));
    }
}
