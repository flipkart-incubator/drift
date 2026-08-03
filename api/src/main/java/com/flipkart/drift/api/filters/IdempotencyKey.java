package com.flipkart.drift.api.filters;

import lombok.Builder;
import lombok.Data;

/**
 * Value object for a resolved business-key idempotency request (§3.4, Redis-free variant).
 * No {@code cacheKey} field — §3.4 has no cache; the derived {@code workflowId} IS the
 * de-dup key, enforced by Temporal itself.
 */
@Data
@Builder
public class IdempotencyKey {
    private final String tenant;
    private final String clientId;
    private final String rawKey;
    private final String workflowId;
}
