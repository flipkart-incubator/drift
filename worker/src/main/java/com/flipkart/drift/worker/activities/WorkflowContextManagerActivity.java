package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.client.request.WorkflowResumeRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowStartRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowUtilityRequest;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "workflowContextManager")
public interface WorkflowContextManagerActivity {
    void persistWorkflowState(WorkflowStartRequest workflowStartRequest, String workflowId);

    void resumeWorkflowState(WorkflowResumeRequest workflowState, String currentNodeRef);

    void disconnectedNodeState(WorkflowUtilityRequest workflowUtilityRequest, String workflowId);
}