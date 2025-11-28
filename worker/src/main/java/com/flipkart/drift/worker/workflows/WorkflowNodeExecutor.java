package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.worker.activities.ReturnControlActivity;
import com.flipkart.drift.worker.activities.WorkflowContextManagerActivity;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.model.activity.ActivityThinRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.commons.model.client.request.WorkflowUtilityRequest;
import com.flipkart.drift.commons.model.client.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.enums.WorkflowUtilityStatus;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.worker.temporal.OptionsStore;
import com.google.common.collect.Sets;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.ActivityStub;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.*;

@Slf4j
public class WorkflowNodeExecutor {
    private final Logger logger = io.temporal.workflow.Workflow.getLogger(WorkflowNodeExecutor.class);
    private final WorkflowState workflowState;
    private final Set<NodeType> localActivityTypes = Sets.newHashSet(NodeType.INSTRUCTION, NodeType. BRANCH,
            NodeType.GROOVY, NodeType.SUCCESS, NodeType.FAILURE);

    public WorkflowNodeExecutor(WorkflowState workflowState) {
        this.workflowState = workflowState;
    }

    public ActivityThinResponse executeNode(WorkflowNode currentNode, Map<String, String> threadContext) {
        return executeNode(currentNode, threadContext, true);
    }

    public void executeNodeWithoutStatusUpdate(WorkflowNode currentNode, Map<String, String> threadContext) {
        executeNode(currentNode, threadContext, false);
    }

    private ActivityThinResponse executeNode(WorkflowNode currentNode, Map<String, String> threadContext, boolean updateState) {
        NodeDefinition nodeDefinition = currentNode.getNodeDefinition();
        if (nodeDefinition == null) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Node definition is null for node: " + currentNode.getInstanceName(),
                    "NODE_DEFINITION_NULL"
            );
        }

        try {
            String logPrefix = updateState ? "Executing node" : "Executing post-workflow node";
            logger.info("{}: {} with type: {}", logPrefix, currentNode.getInstanceName(), nodeDefinition.getType());

            // Determine if we should use local activity or standard activity
            boolean isLocalActivity = localActivityTypes.contains(nodeDefinition.getType());
            ActivityStub activityStub = isLocalActivity ?
                    io.temporal.workflow.Workflow.newUntypedLocalActivityStub(OptionsStore.localActivityOptions) :
                    io.temporal.workflow.Workflow.newUntypedActivityStub(OptionsStore.activityOptionsV1);

            ActivityThinRequest<NodeDefinition> activityRequest = ActivityThinRequest.builder()
                    .workflowId(workflowState.getWorkflowId())
                    .nodeDefinition(nodeDefinition)
                    .workflowNode(currentNode)
                    .threadContext(threadContext)
                    .build();

            // Execute the activity
            ActivityThinResponse response = activityStub.execute(
                    getActivityType(nodeDefinition.getType()),
                    ActivityThinResponse.class,
                    activityRequest
            );

            if (response == null) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Null response from node execution: " + currentNode.getInstanceName(),
                        "ACTIVITY_RESPONSE_NULL"
                );
            }
            if (updateState) {
                updateWorkflowState(response, currentNode);
            }
            return response;

        } catch (Exception e) {
            String errorPrefix = updateState ? "Error executing node" : "Error executing post-workflow node";
            logger.error("{} {}: {}", errorPrefix, currentNode.getInstanceName(), e.getMessage(), e);
            throw e;
        }
    }

    public void handleNodeResponseStatus(String workflowId, ActivityThinResponse activityThinResponse, Workflow workflow, Map<String, String> threadContext) {
        switch (this.workflowState.getStatus()) {
            case WAITING:
                handleWaitingState(workflowId, activityThinResponse);
                break;
            case FAILED:
                handleFailedState(workflowId, activityThinResponse);
                break;
            case COMPLETED:
                handleCompletedState(workflowId, activityThinResponse, workflow, threadContext);
                break;
            case DELEGATED:
                handleDelegatedState(workflowId, activityThinResponse);
                break;
            default:
                logger.warn("Unknown workflow status: {}", this.workflowState.getStatus());
                break;
        }
    }

    public WorkflowNode handleNodeExecutionError(Exception e, Workflow workflow) {
        this.workflowState.setErrorMessage("Error message: " + e.getMessage());
        WorkflowNode fallbackNode = workflow.getStates().get(workflow.getDefaultFailureNode());
        // Fail the workflow if no fallback configured
        if (fallbackNode == null) {
            this.workflowState.setStatus(WorkflowStatus.FAILED);
            throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "Failed to execute node: " + e.getMessage(),
                    "NODE_EXECUTION_FAILED", e
            );
        }
        return fallbackNode;
    }

    public WorkflowUtilityResponse executeWorkflowNode(WorkflowUtilityRequest workflowUtilityRequest, WorkflowNode workflowNode) {
        NodeDefinition nodeDefinition = workflowNode.getNodeDefinition();
        ActivityStub untypedActivityStub = io.temporal.workflow.Workflow.newUntypedActivityStub(OptionsStore.activityOptionsV1);
        ActivityResponse response;
        io.temporal.workflow.Workflow.newActivityStub(WorkflowContextManagerActivity.class, OptionsStore.activityOptions)
                .disconnectedNodeState(workflowUtilityRequest, workflowState.getWorkflowId());
        try {
            logger.info("Executing disconnected node: {} with type: {}", workflowNode.getInstanceName(), nodeDefinition.getType());
            response = untypedActivityStub.execute(
                    nodeDefinition.getType().name().toLowerCase() + "ExecuteWithFatResponse",
                    ActivityResponse.class,
                    ActivityThinRequest.builder()
                            .workflowId(workflowState.getWorkflowId())
                            .nodeDefinition(workflowNode.getNodeDefinition())
                            .workflowNode(workflowNode)
                            .threadContext(workflowUtilityRequest.getThreadContext())
                            .build()
            );

            if (response == null) {
                return buildResponse(workflowUtilityRequest, WorkflowUtilityStatus.FAILURE, new ActivityResponse());
            }
            return buildResponse(workflowUtilityRequest, WorkflowUtilityStatus.SUCCESS, response);
        } catch (Exception e) {
            logger.error("Error while executing disconnected node: {}", e.getMessage(), e);
            return buildResponse(workflowUtilityRequest, WorkflowUtilityStatus.FAILURE, new ActivityResponse());
        }
    }

    private void handleWaitingState(String workflowId, ActivityThinResponse activityThinResponse) {
        this.workflowState.setView(activityThinResponse.getView());
        io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions).exec(workflowId);
        io.temporal.workflow.Workflow.await(() -> {
            WorkflowStatus status = this.workflowState.getStatus();
            return !(status.equals(WorkflowStatus.WAITING) || status.equals(WorkflowStatus.TERMINATED));
        });
    }

    private void handleFailedState(String workflowId, ActivityThinResponse activityThinResponse) {
        if (activityThinResponse.getErrorResponse() != null) {
            this.workflowState.setErrorMessage(activityThinResponse.getErrorResponse().asText());
        }
        io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions).exec(workflowId);
        throw ApplicationFailure.newNonRetryableFailure(
                "Encountered a failure node",
                "FAILURE_NODE"
        );
    }

    private void handleCompletedState(String workflowId, ActivityThinResponse activityThinResponse, Workflow workflow, Map<String, String> threadContext) {
        this.workflowState.setView(activityThinResponse.getView());
        this.workflowState.setDisposition(activityThinResponse.getDisposition());
        io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions)
                .exec(workflowId);

        // Execute post-workflow completion nodes if they exist
        if (workflow != null &&
            workflow.getPostWorkflowCompletionNodes() != null &&
            !workflow.getPostWorkflowCompletionNodes().isEmpty()) {
            logger.info("Executing post-workflow completion nodes for workflow: {}", workflowId);
            executePostWorkflowCompletionNodes(workflow, threadContext);
        }
    }

    private void executePostWorkflowCompletionNodes(Workflow workflow, Map<String, String> threadContext) {
        try {
            for (String nodeId : workflow.getPostWorkflowCompletionNodes()) {
                WorkflowNode postNode = workflow.getStates().get(nodeId);
                if (postNode == null) {
                    logger.warn("Post-workflow completion node {} not found in workflow states", nodeId);
                    continue;
                }
                logger.info("Executing post-completion node: {}", postNode.getInstanceName());
                executeNodeWithoutStatusUpdate(postNode, threadContext);
            }
        } catch (Exception e) {
            logger.error("Error executing post-workflow completion nodes: {}", e.getMessage(), e);
            // Non-blocking - we don't want post-completion nodes to affect workflow completion
        }
    }

    private void handleDelegatedState(String workflowId, ActivityThinResponse activityThinResponse) {
        io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions)
                .exec(workflowId);
    }

    private void updateWorkflowState(ActivityThinResponse response, WorkflowNode currentNode) {
        this.workflowState.setStatus(response.getWorkflowStatus());
        this.workflowState.setCurrentNodeRef(generateNodeIdentifier(currentNode));
        if (Optional.ofNullable(response.getNextNode()).isPresent()) {
            currentNode.setNextNode(response.getNextNode());
        }
    }

    private String getActivityType(NodeType type) {
        return type.name().toLowerCase() + "Execute";
    }

    private String generateNodeIdentifier(WorkflowNode currentNode) {
        if (currentNode.getContextOverrideKey() != null) {
            return currentNode.getContextOverrideKey();
        }
        return currentNode.getInstanceName();
    }

    private WorkflowUtilityResponse buildResponse(WorkflowUtilityRequest request, WorkflowUtilityStatus status, ActivityResponse response) {
        return WorkflowUtilityResponse.builder()
                .node(request.getNode())
                .workflowId(workflowState.getWorkflowId())
                .status(status)
                .response(response.getNodeResponse())
                .build();
    }
}
