package com.flipkart.drift.api.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Configuration for business-key idempotency on {@code POST /v3/workflow/start}.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdempotencyConfig {
    /** Request header name(s) checked for a client-supplied idempotency key. */
    private List<String> headers = List.of("X-Drift-Idempotency-Key");
    /** Whether requests without an idempotency key header are allowed through unchanged. */
    private boolean optional = true;
    /**
     * When true, {@code rawKey} is used as the workflowId verbatim, with no tenant/clientId
     * scoping and no hash. Only safe when rawKey is already guaranteed globally unique
     * across all tenants/clients (e.g. caller-generated UUIDs), or this deployment is
     * single-tenant/single-client. Operators enabling this take on full responsibility for
     * that uniqueness contract and for rawKey being non-sensitive (Temporal workflowIds are
     * visible in the Temporal UI/CLI to anyone with namespace access) — Drift performs no
     * collision-prevention of its own on this path.
     */
    private boolean useRawKeyAsWorkflowId = false;
}
