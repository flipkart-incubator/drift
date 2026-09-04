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
     * Header that, when set to "true", tells Drift to use the raw idempotency key
     * value directly as the Temporal workflowId instead of deriving a hashed one.
     */
    private String useKeyAsWorkflowIdHeader = "X-Drift-Idempotency-Key-As-Workflow-Id";
}
