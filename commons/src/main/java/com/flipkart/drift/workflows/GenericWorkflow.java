package com.flipkart.drift.workflows;

import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import io.temporal.workflow.*;


@WorkflowInterface
public interface GenericWorkflow {

    @WorkflowMethod
    void startWorkflow(WorkflowStartRequest workflowStartRequest);

    @SignalMethod
    void resumeWorkflow(WorkflowResumeRequest workflowResumeRequest);

    @SignalMethod
    void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest);

    @QueryMethod
    WorkflowState getWorkflowState();

    @UpdateMethod
    WorkflowUtilityResponse executeDisconnectedNode(WorkflowUtilityRequest workflowUtilityRequest);
}
