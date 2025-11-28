package com.flipkart.drift.workflows;

import com.flipkart.drift.commons.model.client.request.WorkflowResumeRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowStartRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowTerminateRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowUtilityRequest;
import com.flipkart.drift.commons.model.client.response.WorkflowUtilityResponse;
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
