package com.flipkart.drift.worker.activities;

import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "workflowContextManager")
public interface WorkflowContextManagerActivity {
    void persistWorkflowState(WorkflowStartRequest workflowStartRequest, String workflowId);

    void resumeWorkflowState(WorkflowResumeRequest workflowState, String currentNodeRef);

    void disconnectedNodeState(WorkflowUtilityRequest workflowUtilityRequest, String workflowId);
}