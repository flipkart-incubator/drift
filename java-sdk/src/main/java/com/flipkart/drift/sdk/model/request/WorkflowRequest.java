package com.flipkart.drift.sdk.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class WorkflowRequest {
    private String incidentId;
    /**
     * Legacy/manual Temporal workflow identifier. Prefer letting Drift derive the
     * workflow id automatically from the {@code X_DRIFT_IDEMPOTENCY_KEY} request header
     * on {@code POST /v3/workflow/start} instead of setting this field directly — the
     * header-derived id gives at-most-one-execution-per-business-key semantics via
     * Temporal's atomic start, whereas a repeated explicit {@code workflowId} here
     * terminates and replaces any running execution. This field remains fully
     * functional for existing callers (no behavior change).
     */
    private String workflowId;
    private String parentWorkflowId;
    private Map<String, String> threadContext;
    private Map<String, Object> params;
}
