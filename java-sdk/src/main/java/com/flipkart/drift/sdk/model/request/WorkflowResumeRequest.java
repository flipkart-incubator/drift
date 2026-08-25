package com.flipkart.drift.sdk.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.sdk.model.enums.WorkflowExecutionMode;
import com.flipkart.drift.sdk.model.response.ViewResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowResumeRequest extends WorkflowRequest {
    // Nullable for external event resumes — event payload goes in params instead.
    private ViewResponse viewResponse;
    /**
     * If set, overrides the execution mode stored on the workflow.
     * External event consumers should set this to ASYNC (no Redis, fire-and-forget).
     * Null means fall back to the mode the workflow was originally started with.
     */
    private WorkflowExecutionMode workflowExecutionMode;
    /**
     * Identifies which external event this resume call represents (e.g. "ORDER_DELIVERED").
     * Used for N-event tracking: the signal handler accumulates received event types in WorkflowState
     * and unblocks the workflow only when the ANY / ALL condition is satisfied.
     * Null for legacy human-in-the-loop resumes where the first call always unblocks.
     */
    private String eventType;

}