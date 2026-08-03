package com.flipkart.drift.api.it;

import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.workflows.GenericWorkflow;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

/**
 * Minimal test-only {@link GenericWorkflow} implementation used exclusively by
 * {@link BusinessKeyIdempotencyIT} against Temporal's in-memory {@code TestWorkflowEnvironment}.
 * The real implementation lives in the {@code worker} module, which {@code api} must never
 * depend on (ARCHITECTURE_RULES.md, {@code apiMustNotDependOnWorker}) -- this fake stands in
 * for it so the integration test can exercise a genuine Temporal round-trip without violating
 * that boundary. Deliberately trivial: honors an optional {@code shouldFail} param to simulate
 * a FAILED run for the retry-after-failure scenario.
 */
public class FakeGenericWorkflowImpl implements GenericWorkflow {

    private WorkflowStatus status = WorkflowStatus.RUNNING;
    private String incidentId;

    private String workflowId;

    @Override
    public void startWorkflow(WorkflowStartRequest workflowStartRequest) {
        workflowId = Workflow.getInfo().getWorkflowId();
        incidentId = workflowStartRequest.getIncidentId();
        boolean shouldFail = workflowStartRequest.getParams() != null
                && Boolean.TRUE.equals(workflowStartRequest.getParams().get("shouldFail"));
        if (shouldFail) {
            status = WorkflowStatus.FAILED;
            throw ApplicationFailure.newNonRetryableFailure("simulated failure", "TestFailure");
        }
        status = WorkflowStatus.COMPLETED;
    }

    @Override
    public void resumeWorkflow(WorkflowResumeRequest workflowResumeRequest) {
        // not exercised by this integration test
    }

    @Override
    public void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest) {
        status = WorkflowStatus.TERMINATED;
    }

    @Override
    public WorkflowState getWorkflowState() {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId(workflowId);
        state.setIncidentId(incidentId);
        state.setStatus(status);
        return state;
    }

    @Override
    public WorkflowUtilityResponse executeDisconnectedNode(WorkflowUtilityRequest workflowUtilityRequest) {
        return null;
    }
}
