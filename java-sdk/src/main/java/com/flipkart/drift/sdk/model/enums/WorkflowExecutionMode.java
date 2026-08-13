package com.flipkart.drift.sdk.model.enums;

/**
 * Controls how the API call behaves when starting or resuming a workflow.
 * SYNC  - the API thread blocks until the workflow reaches a terminal state (requires Redis).
 * ASYNC - the API returns immediately with RUNNING status; the workflow completes in the background.
 * Defaults to SYNC for backward compatibility.
 */
public enum WorkflowExecutionMode {
    ASYNC,
    SYNC
}
