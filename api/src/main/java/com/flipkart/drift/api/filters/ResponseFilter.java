package com.flipkart.drift.api.filters;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class ResponseFilter implements ContainerResponseFilter {

    public static final String WORKFLOW_ID_HEADER = "X-Drift-Workflow-Id";
    public static final String IDEMPOTENT_REPLAY_HEADER = "X-Drift-Idempotent-Replay";

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) {
        containerResponseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
        containerResponseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        String requestHeader = containerRequestContext.getHeaders().getFirst("Access-Control-Request-Headers");
        if (requestHeader != null) {
            containerResponseContext.getHeaders().add("Access-Control-Allow-Headers", requestHeader);
        }
        containerResponseContext.getHeaders().add("X_SERVER_TRACE_ID", MDC.get("id"));
        addIdempotencyResponseHeaders(containerResponseContext);
        this.removeFromMDC();
    }

    /**
     * §3.4 business-key idempotency response headers (LLD §4.2 + plan Clarification #2):
     * {@code X-Drift-Workflow-Id} is set whenever the request resolved to a business-key
     * derived workflowId; {@code X-Drift-Idempotent-Replay: true} is added only when that
     * resolution hit a pre-existing Temporal execution (never {@code "false"} -- omitted
     * entirely on a genuine fresh start, and omitted entirely for legacy/non-idempotent
     * requests where {@code resolvedWorkflowId} is blank).
     */
    private void addIdempotencyResponseHeaders(ContainerResponseContext containerResponseContext) {
        RequestThreadContext threadContext = RequestThreadContext.get();
        String resolvedWorkflowId = threadContext.getResolvedWorkflowId();
        if (StringUtils.isBlank(resolvedWorkflowId)) {
            return;
        }
        containerResponseContext.getHeaders().add(WORKFLOW_ID_HEADER, resolvedWorkflowId);
        if (threadContext.isResolvedFromExistingWorkflow()) {
            containerResponseContext.getHeaders().add(IDEMPOTENT_REPLAY_HEADER, "true");
        }
    }

    private void removeFromMDC() {
        MDC.remove("id");
        MDC.remove("idempotencyKey");
        RequestThreadContext.remove();
    }
}




