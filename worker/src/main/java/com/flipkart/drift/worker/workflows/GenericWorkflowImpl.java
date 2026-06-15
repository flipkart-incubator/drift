package com.flipkart.drift.worker.workflows;

import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.worker.activities.FetchWorkflowActivity;
import com.flipkart.drift.worker.activities.WorkflowContextManagerActivity;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.sdk.model.enums.WorkflowExecutionMode;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.worker.temporal.OptionsStore;
import io.temporal.failure.ApplicationFailure;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Slf4j
public class GenericWorkflowImpl implements com.flipkart.drift.workflows.GenericWorkflow {
    private static final ClassLoader classLoader = GenericWorkflowImpl.class.getClassLoader();
    private final Logger logger = io.temporal.workflow.Workflow.getLogger(GenericWorkflowImpl.class);
    private final WorkflowNodeExecutor nodeExecutor;
    private WorkflowState workflowState;

    public GenericWorkflowImpl() {
        this.workflowState = new WorkflowState();
        this.nodeExecutor = new WorkflowNodeExecutor(workflowState);
    }

    @Override
    @Timed(name = "workflow.start.duration")
    public void startWorkflow(WorkflowStartRequest workflowStartRequest) {
        try {
            initializeWorkflow(workflowStartRequest);
            Workflow workflow = fetchDsl(workflowStartRequest);
            if (workflow == null) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Workflow not found for issue: " + workflowStartRequest.getIssueDetail().getIssueId(),
                        "WORKFLOW_NOT_FOUND"
                );
            }
            WorkflowNode currentNode = workflow.getStates().get(workflow.getStartNode());
            if (currentNode == null) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Start node not found in workflow: " + workflow.getStartNode(),
                        "START_NODE_NOT_FOUND"
                );
            }
            executeWorkflowNodes(workflow, currentNode, workflowStartRequest.getWorkflowId(), workflowStartRequest.getThreadContext(), workflowStartRequest);

        } catch (Exception e) {
            logger.error("Error while executing workflow: {}", e.getMessage(), e);
            this.workflowState.setStatus(WorkflowStatus.FAILED);
            if (this.workflowState.getErrorMessage() == null) {
                this.workflowState.setErrorMessage(e.getMessage());
            }
            if (e instanceof ApplicationFailure) {
                throw e;
            }
            throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "Error while executing workflow: " + e.getMessage(),
                    "WORKFLOW_FAILED", e
            );
        }
    }

    @Override
    @Timed(name = "workflow.resume.duration")
    public void resumeWorkflow(WorkflowResumeRequest workflowResumeRequest) {
        try {
            io.temporal.workflow.Workflow.newActivityStub(WorkflowContextManagerActivity.class, OptionsStore.activityOptions)
                    .resumeWorkflowState(workflowResumeRequest, this.workflowState.getCurrentNodeRef());

            // Track the received event type for ON_EVENT multi-event waits.
            String eventType = workflowResumeRequest.getEventType();
            if (eventType != null) {
                if (this.workflowState.getReceivedEventTypes() == null) {
                    this.workflowState.setReceivedEventTypes(new java.util.HashSet<>());
                }
                this.workflowState.getReceivedEventTypes().add(eventType);
            }

            if (isResumeConditionMet()) {
                this.workflowState.setStatus(WorkflowStatus.RUNNING);
            }
            // else: waitSemantics == ALL and not all events received yet —
            // status stays WAITING, Workflow.await() re-evaluates and stays blocked.
        } catch (Exception e) {
            logger.error("Error resuming workflow: {}", e.getMessage(), e);
            this.workflowState.setStatus(WorkflowStatus.FAILED);
            this.workflowState.setErrorMessage(e.getMessage());
            throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "Failed to resume workflow: " + e.getMessage(),
                    "RESUME_FAILED", e
            );
        }
    }

    /**
     * Returns true when the workflow should transition from WAITING to RUNNING.
     *
     * Legacy path (no ON_EVENT semantics): waitSemantics is null → first call always unblocks.
     * ANY: first event received unblocks.
     * ALL: every event in expectedEventTypes must be present in receivedEventTypes.
     */
    private boolean isResumeConditionMet() {
        WaitSemantics semantics = this.workflowState.getWaitSemantics();
        if (semantics == null) {
            return true;
        }
        Set<String> received = this.workflowState.getReceivedEventTypes();
        List<String> expected = this.workflowState.getExpectedEventTypes();
        boolean met;
        if (semantics == WaitSemantics.ANY) {
            met = received != null && !received.isEmpty();
        } else {
            // ALL
            met = received != null && expected != null && received.containsAll(expected);
        }
        if (met) {
            drainConsumedEvents(expected, received);
        }
        return met;
    }

    // Remove events that were consumed by the current WaitNode so they don't
    // pollute the receivedEventTypes set for subsequent WaitNodes.
    private void drainConsumedEvents(List<String> expected, Set<String> received) {
        if (expected != null && received != null) {
            received.removeAll(expected);
        }
    }

    @Override
    public void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest) {
        this.workflowState.setStatus(WorkflowStatus.TERMINATED);
    }


    @Override
    public WorkflowState getWorkflowState() {
        return this.workflowState;
    }

    private void executeWorkflowNodes(Workflow workflow, WorkflowNode currentNode, String workflowId, Map<String, String> threadContext, WorkflowStartRequest workflowStartRequest) {
        while (currentNode != null) {
            ActivityThinResponse activityThinResponse;
            try {
                logger.info("WfId : {} Running node: {}", workflowId, currentNode.getInstanceName());
                activityThinResponse = nodeExecutor.executeNode(currentNode, threadContext, workflowStartRequest);
            } catch (Exception e) {
                currentNode = nodeExecutor.handleNodeExecutionError(e, workflow);
                continue;
            }
            if (activityThinResponse != null) {
                nodeExecutor.handleNodeResponseStatus(workflowId, activityThinResponse, workflow, threadContext);
            }
            currentNode = workflow.getStates().get(currentNode.getNextNode());
        }
    }

    private void initializeWorkflow(WorkflowStartRequest workflowStartRequest) {
        this.workflowState.setIncidentId(workflowStartRequest.getIncidentId());
        this.workflowState.setWorkflowId(io.temporal.workflow.Workflow.getInfo().getWorkflowId());
        this.workflowState.setStatus(WorkflowStatus.CREATED);
        this.workflowState.setIssueDetail(workflowStartRequest.getIssueDetail());
        this.workflowState.setWorkflowExecutionMode(
                workflowStartRequest.getWorkflowExecutionMode() != null
                        ? workflowStartRequest.getWorkflowExecutionMode()
                        : WorkflowExecutionMode.SYNC
        );
        io.temporal.workflow.Workflow.newActivityStub(WorkflowContextManagerActivity.class, OptionsStore.activityOptions)
                .persistWorkflowState(workflowStartRequest, io.temporal.workflow.Workflow.getInfo().getWorkflowId());
    }

    private Workflow fetchDsl(WorkflowStartRequest workflowRequest) {
        log.info("Fetching workflow DSL for issueId: {}", workflowRequest.getIssueDetail().getIssueId());
        FetchWorkflowActivity fetchWorkflowActivity = io.temporal.workflow.Workflow.newActivityStub(
                FetchWorkflowActivity.class, OptionsStore.activityOptions);
        return fetchWorkflowActivity.fetchWorkflowBasedOnRequest(workflowRequest);

    }

    @Timed(name = "workflow.execute.disconnected.duration")
    @Override
    public WorkflowUtilityResponse executeDisconnectedNode(WorkflowUtilityRequest workflowUtilityRequest) {
        String tenant = workflowUtilityRequest.getThreadContext().getOrDefault("tenant", "fk");
        WorkflowNode workflowNode = io.temporal.workflow.Workflow.newActivityStub(
                FetchWorkflowActivity.class,
                OptionsStore.activityOptions).fetchWorkflowNode(
                this.workflowState.getIssueDetail().getIssueId(),
                workflowUtilityRequest.getNode(), tenant);
        return nodeExecutor.executeWorkflowNode(workflowUtilityRequest, workflowNode);
    }
}