package com.flipkart.drift.api.filters;

import com.flipkart.drift.api.config.IdempotencyConfig;
import com.flipkart.drift.api.service.idempotency.IdempotencyMetrics;
import com.flipkart.drift.api.service.utils.IdempotencyKeyResolver;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the business-key idempotency header on {@code POST /v3/workflow/start} and
 * stashes the derived workflowId on {@link RequestThreadContext}.
 * <p>
 * Request-filter only — there is no response-filter method here; the actual de-dup is
 * arbitrated server-side by Temporal's atomic {@code StartWorkflowExecution} inside
 * {@code TemporalService}. This filter never returns a {@code 409}.
 */
@Provider
@Slf4j
public class IdempotencyFilter implements ContainerRequestFilter {

    private static final Set<String> APPLY_TO_PATHS = Set.of("/v3/workflow/start");

    private final IdempotencyConfig idempotencyConfig;
    private final IdempotencyKeyResolver resolver;
    private final IdempotencyMetrics metrics;

    @Inject
    public IdempotencyFilter(IdempotencyConfig idempotencyConfig, IdempotencyKeyResolver resolver,
                              IdempotencyMetrics metrics) {
        this.idempotencyConfig = idempotencyConfig;
        this.resolver = resolver;
        this.metrics = metrics;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!shouldApply(requestContext)) {
            return;
        }

        Optional<String> rawKeyOpt = resolver.extractRawKey(requestContext.getHeaders());
        if (rawKeyOpt.isEmpty()) {
            // optional=true, no header present -- legacy fall-through, unchanged behavior
            return;
        }

        String rawKey = rawKeyOpt.get();
        RequestThreadContext threadContext = RequestThreadContext.get();
        String tenant = threadContext.getTenant();
        String clientId = threadContext.getClientId();

        IdempotencyKey key = resolver.resolve(tenant, clientId, rawKey);
        threadContext.setResolvedWorkflowId(key.getWorkflowId());
        threadContext.setIdempotencyKey(rawKey);

        MDC.put("idempotencyKey", rawKey);
        metrics.miss(tenant, clientId);
    }

    private boolean shouldApply(ContainerRequestContext requestContext) {
        String path = "/" + requestContext.getUriInfo().getPath();
        return APPLY_TO_PATHS.contains(path);
    }
}
