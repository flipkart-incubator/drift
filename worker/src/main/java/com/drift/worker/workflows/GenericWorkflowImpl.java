package com.drift.worker.workflows;

import com.codahale.metrics.annotation.Timed;
import com.drift.worker.activities.FetchWorkflowActivity;
import com.drift.worker.activities.WorkflowContextManagerActivity;
import com.drift.worker.model.activity.ActivityThinResponse;
import com.drift.commons.model.client.request.WorkflowResumeRequest;
import com.drift.commons.model.client.request.WorkflowStartRequest;
import com.drift.commons.model.client.request.WorkflowTerminateRequest;
import com.drift.commons.model.client.request.WorkflowUtilityRequest;
import com.drift.commons.model.client.response.WorkflowUtilityResponse;
import com.drift.commons.model.enums.WorkflowStatus;
import com.drift.commons.model.node.Workflow;
import com.drift.commons.model.node.WorkflowNode;
import com.drift.commons.model.temporal.WorkflowState;
import com.drift.worker.temporal.OptionsStore;
import io.temporal.failure.ApplicationFailure;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.Map;

@Data
@Slf4j
public class GenericWorkflowImpl implements com.drift.workflows.GenericWorkflow {
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
            executeWorkflowNodes(workflow, currentNode, workflowStartRequest.getWorkflowId(), workflowStartRequest.getThreadContext());

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
            this.workflowState.setStatus(WorkflowStatus.RUNNING);
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

    @Override
    public void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest) {
        this.workflowState.setStatus(WorkflowStatus.TERMINATED);
    }


    @Override
    public WorkflowState getWorkflowState() {
        return this.workflowState;
    }

    private void executeWorkflowNodes(Workflow workflow, WorkflowNode currentNode, String workflowId, Map<String, String> threadContext) {
        while (currentNode != null) {
            ActivityThinResponse activityThinResponse;
            try {
                logger.info("WfId : {} Running node: {}", workflowId, currentNode.getInstanceName());
                activityThinResponse = nodeExecutor.executeNode(currentNode, threadContext);
            } catch (Exception e) {
                currentNode = nodeExecutor.handleNodeExecutionError(e, workflow);
                continue;
            }
            nodeExecutor.handleNodeResponseStatus(workflowId, activityThinResponse, workflow, threadContext);
            currentNode = workflow.getStates().get(currentNode.getNextNode());
        }
    }

    private void initializeWorkflow(WorkflowStartRequest workflowStartRequest) {
        this.workflowState.setIncidentId(workflowStartRequest.getIncidentId());
        this.workflowState.setWorkflowId(io.temporal.workflow.Workflow.getInfo().getWorkflowId());
        this.workflowState.setStatus(WorkflowStatus.CREATED);
        this.workflowState.setIssueDetail(workflowStartRequest.getIssueDetail());
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