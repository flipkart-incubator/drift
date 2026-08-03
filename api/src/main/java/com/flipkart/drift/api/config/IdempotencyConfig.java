package com.flipkart.drift.api.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Configuration for business-key idempotency on {@code POST /v3/workflow/start}.
 * <p>
 * Redis-free variant: no {@code ttl}, {@code pendingTtl}, or {@code replayCachedResponse}
 * fields — those are Redis-backed-cache concepts and are intentionally out of scope for
 * this deployment.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdempotencyConfig {
    private List<String> headers = List.of("X_DRIFT_IDEMPOTENCY_KEY");
    private boolean optional = true;
}
