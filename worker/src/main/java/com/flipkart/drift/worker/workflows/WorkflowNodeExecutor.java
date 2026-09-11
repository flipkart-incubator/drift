package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.sdk.model.enums.WorkflowExecutionMode;
import com.flipkart.drift.commons.model.node.ChildNode;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.worker.activities.ReturnControlActivity;
import com.flipkart.drift.worker.activities.WorkflowContextManagerActivity;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.model.activity.ActivityThinRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.enums.WorkflowUtilityStatus;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.worker.temporal.ActivityOptionsBuilderHolder;
import com.flipkart.drift.worker.temporal.OptionsStore;
import com.flipkart.drift.workflows.GenericWorkflow;
import com.google.common.collect.Sets;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.ActivityStub;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.*;
import java.util.List;

import static com.flipkart.drift.worker.Utility.WorkerUtility.generateChildWfId;
import static com.flipkart.drift.worker.util.Constants.VERSION;
import static com.flipkart.drift.worker.util.Constants.WORKFLOW_ID;

@Slf4j
public class WorkflowNodeExecutor {
    private final Logger logger = io.temporal.workflow.Workflow.getLogger(WorkflowNodeExecutor.class);
    private final WorkflowState workflowState;
    private final Set<NodeType> localActivityTypes = Sets.newHashSet(NodeType.INSTRUCTION, NodeType. BRANCH,
            NodeType.GROOVY, NodeType.SUCCESS, NodeType.FAILURE);

    public WorkflowNodeExecutor(WorkflowState workflowState) {
        this.workflowState = workflowState;
    }

    public ActivityThinResponse executeNode(WorkflowNode currentNode, Map<String, String> threadContext, WorkflowStartRequest workflowStartRequest) {
        if (currentNode.getNodeDefinition().getType() == NodeType.CHILD) {
            if (workflowStartRequest.getParentWorkflowId() != null) {
                throw ApplicationFailure.newNonRetryableFailure("Child node cannot be nested inside another child workflow: " + currentNode.getInstanceName(), "INVALID_CHILD_NODE");
            }
            invokeChild(workflowStartRequest, currentNode);
            return null;
        }
        return executeNode(currentNode, threadContext, true);
    }

    public void executeNodeWithoutStatusUpdate(WorkflowNode currentNode, Map<String, String> threadContext) {
        executeNode(currentNode, threadContext, false);
    }

    public ActivityThinResponse executeParallelNode(WorkflowNode currentNode, Map<String, String> threadContext,
                                                     WorkflowStartRequest workflowStartRequest) {
        if (currentNode.getNodeDefinition().getType() == NodeType.CHILD) {
            invokeChild(workflowStartRequest, currentNode);
            return null;
        }
        return executeNodeInternal(currentNode, threadContext, true, false);
    }

    private ActivityThinResponse executeNodeInternal(WorkflowNode currentNode, Map<String, String> threadContext,
                                                      boolean parallelExecution, boolean updateState) {
        NodeDefinition nodeDefinition = currentNode.getNodeDefinition();
        if (nodeDefinition == null) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Node definition is null for node: " + currentNode.getInstanceName(),
                    "NODE_DEFINITION_NULL"
            );
        }

        try {
            logger.info("Executing node: {} with type: {} (parallel={})",
                    currentNode.getInstanceName(), nodeDefinition.getType(), parallelExecution);

            boolean isLocalActivity = localActivityTypes.contains(nodeDefinition.getType());
            ActivityStub activityStub = isLocalActivity ?
                    io.temporal.workflow.Workflow.newUntypedLocalActivityStub(OptionsStore.localActivityOptions) :
                    io.temporal.workflow.Workflow.newUntypedActivityStub(
                            ActivityOptionsBuilderHolder.get().build(currentNode));

            ActivityThinRequest<NodeDefinition> activityRequest = ActivityThinRequest.builder()
                    .workflowId(workflowState.getWorkflowId())
                    .nodeDefinition(nodeDefinition)
                    .workflowNode(currentNode)
                    .threadContext(threadContext)
                    .parallelExecution(parallelExecution)
                    .build();

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
            if (!parallelExecution && updateState) {
                updateWorkflowState(response, currentNode);
                applyOnEventWaitState(currentNode, nodeDefinition, response);
            }
            return response;

        } catch (Exception e) {
            logger.error("Error executing node {}: {}", currentNode.getInstanceName(), e.getMessage(), e);
            throw e;
        }
    }

    private ActivityThinResponse executeNode(WorkflowNode currentNode, Map<String, String> threadContext, boolean updateState) {
        return executeNodeInternal(currentNode, threadContext, false, updateState);
    }

    public void handleNodeResponseStatus(String workflowId, ActivityThinResponse activityThinResponse, Workflow workflow, Map<String, String> threadContext) {
        switch (this.workflowState.getStatus()) {
            case WAITING:
                handleWaitingState(workflowId, activityThinResponse);
                break;
            case SCHEDULER_WAITING:
                handleSchedulerWaitingState(workflowId, activityThinResponse);
                break;
            case FAILED:
                handleFailedState(workflowId, activityThinResponse);
                break;
            case COMPLETED:
                handleCompletedState(workflowId, activityThinResponse, workflow, threadContext);
                break;
            case ASYNC_COMPLETE:
                handleAsyncCompleteState(workflowId, activityThinResponse, workflow, threadContext);
                break;
            case DELEGATED:
                handleDelegatedState(workflowId, activityThinResponse);
                break;
            case RUNNING:
                // Early ON_EVENT signal(s) satisfied the condition before the WaitNode was reached.
                // No park needed — execution continues to the next node normally.
                break;
            case SIDELINED:
                // SIDELINED pause/unsideline semantics exist only in ParallelWorkflowEngine today —
                // serial execution has no signal handler or park loop for it. Reject explicitly
                // rather than silently treating it like RUNNING/advancing past it.
                throw ApplicationFailure.newNonRetryableFailure(
                        "SIDELINED status is not supported in serial workflow execution",
                        "SIDELINED_NOT_SUPPORTED_SERIAL"
                );
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
        ActivityStub untypedActivityStub = io.temporal.workflow.Workflow.newUntypedActivityStub(
                ActivityOptionsBuilderHolder.get().build(workflowNode));
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

    /**
     * After an activity completes, check whether ON_EVENT wait state needs to be initialised.
     * Two sources: WaitNode with ON_EVENT config (Approach 1) and inline waitConfig on the
     * WorkflowNode wrapper (Approach 2). Both funnel into the same Workflow.await() park.
     */
    private void applyOnEventWaitState(WorkflowNode currentNode, NodeDefinition nodeDefinition, ActivityThinResponse response) {
        // Approach 1: WaitNode with ON_EVENT config — activity already returned WAITING;
        // copy the event config into WorkflowState so the signal handler can evaluate it.
        // resolvedExpectedEventTypes from the activity takes priority (Groovy script result).
        if (nodeDefinition.getType() == NodeType.WAIT) {
            WaitNode waitNode = (WaitNode) nodeDefinition;
            if (WaitType.ON_EVENT == waitNode.getWaitType()) {
                initOnEventState(waitNode.getTypedConfig(OnEventConfig.class), response.getResolvedExpectedEventTypes());
                if (isOnEventConditionMet()) {
                    // Early signal(s) already satisfied the condition before we reached this WaitNode.
                    // Override WAITING back to RUNNING so Workflow.await() unblocks immediately.
                    logger.info("WfId: {} ON_EVENT condition already met by early signal(s), skipping park", workflowState.getWorkflowId());
                    this.workflowState.setStatus(WorkflowStatus.RUNNING);
                }
            }
            return;
        }

        // Approach 2: any node type can declare an optional inline waitConfig.
        // Dynamic resolution via script is not supported here (v1) — workflow thread cannot
        // call HBase. Only static expectedEventTypes is used for inline waitConfig.
        WaitConfig inlineWait = currentNode.getWaitConfig();
        if (inlineWait != null && WaitType.ON_EVENT == inlineWait.getWaitType()) {
            initOnEventState((OnEventConfig) inlineWait, response.getResolvedExpectedEventTypes());
            if (isOnEventConditionMet()) {
                logger.info("WfId: {} inline ON_EVENT condition already met by early signal(s), skipping park", workflowState.getWorkflowId());
            } else {
                this.workflowState.setStatus(WorkflowStatus.WAITING);
            }
        }
    }

    /**
     * @param resolvedTypes  Groovy-resolved list from the activity (non-null → takes priority).
     *                       Null for inline waitConfig path where script resolution is not supported.
     */
    private void initOnEventState(OnEventConfig config, List<String> resolvedTypes) {
        List<String> effective = (resolvedTypes != null && !resolvedTypes.isEmpty())
                ? resolvedTypes
                : config.getExpectedEventTypes();
        this.workflowState.setExpectedEventTypes(effective);
        this.workflowState.setWaitSemantics(config.getWaitSemantics() != null ? config.getWaitSemantics() : WaitSemantics.ALL);
        // Preserve any events received before this WaitNode was reached (early signals).
        // Only initialise the set if it has never been populated.
        if (this.workflowState.getReceivedEventTypes() == null) {
            this.workflowState.setReceivedEventTypes(new HashSet<>());
        }
    }

    private boolean isOnEventConditionMet() {
        WaitSemantics semantics = this.workflowState.getWaitSemantics();
        Set<String> received = this.workflowState.getReceivedEventTypes();
        List<String> expected = this.workflowState.getExpectedEventTypes();
        boolean met;
        if (semantics == WaitSemantics.ANY) {
            met = received != null && expected != null && !Collections.disjoint(received, expected);
        } else {
            // ALL
            met = received != null && expected != null && received.containsAll(expected);
        }
        if (met && expected != null && received != null) {
            received.removeAll(expected);
        }
        return met;
    }

    private void handleWaitingState(String workflowId, ActivityThinResponse activityThinResponse) {
        this.workflowState.setView(activityThinResponse.getView());
        // Notify API via Redis only in SYNC mode; ASYNC workflows don't block the API thread
        if (workflowState.getWorkflowExecutionMode() != WorkflowExecutionMode.ASYNC) {
            io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions).exec(workflowId);
        }
        io.temporal.workflow.Workflow.await(() -> {
            WorkflowStatus status = this.workflowState.getStatus();
            return !(status.equals(WorkflowStatus.WAITING) || status.equals(WorkflowStatus.TERMINATED));
        });
    }

    private void handleSchedulerWaitingState(String workflowId, ActivityThinResponse activityThinResponse) {
        this.workflowState.setView(activityThinResponse.getView());
        io.temporal.workflow.Workflow.await(() -> !this.workflowState.getStatus().equals(WorkflowStatus.SCHEDULER_WAITING));
    }

    private void handleAsyncCompleteState(String workflowId, ActivityThinResponse activityThinResponse, Workflow workflow, Map<String, String> threadContext) {
        this.workflowState.setView(activityThinResponse.getView());
        this.workflowState.setDisposition(activityThinResponse.getDisposition());

        // Execute post-workflow completion nodes if they exist
        if (workflow != null && workflow.getPostWorkflowCompletionNodes() != null && !workflow.getPostWorkflowCompletionNodes().isEmpty()) {
            logger.info("Executing post-workflow completion nodes for workflow: {}", workflowId);
            executePostWorkflowCompletionNodes(workflow, threadContext);
        }
    }

    private void handleFailedState(String workflowId, ActivityThinResponse activityThinResponse) {
        if (activityThinResponse.getErrorResponse() != null) {
            this.workflowState.setErrorMessage(activityThinResponse.getErrorResponse().asText());
        }
        if (workflowState.getWorkflowExecutionMode() != WorkflowExecutionMode.ASYNC) {
            io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions).exec(workflowId);
        }
        throw ApplicationFailure.newNonRetryableFailure(
                "Encountered a failure node",
                "FAILURE_NODE"
        );
    }

    private void handleCompletedState(String workflowId, ActivityThinResponse activityThinResponse, Workflow workflow, Map<String, String> threadContext) {
        this.workflowState.setView(activityThinResponse.getView());
        this.workflowState.setDisposition(activityThinResponse.getDisposition());
        if (workflowState.getWorkflowExecutionMode() != WorkflowExecutionMode.ASYNC) {
            io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions).exec(workflowId);
        }

        // Execute post-workflow completion nodes if they exist
        if (workflow != null && workflow.getPostWorkflowCompletionNodes() != null && !workflow.getPostWorkflowCompletionNodes().isEmpty()) {
            logger.info("Executing post-workflow completion nodes for workflow: {}", workflowId);
            executePostWorkflowCompletionNodes(workflow, threadContext);
        }
    }

    public void runPostWorkflowCompletionNodes(Workflow workflow, Map<String, String> threadContext) {
        executePostWorkflowCompletionNodes(workflow, threadContext);
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
        if (workflowState.getWorkflowExecutionMode() != WorkflowExecutionMode.ASYNC) {
            io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions)
                    .exec(workflowId);
        }
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

    public void invokeChild(WorkflowStartRequest workflowStartRequest, WorkflowNode currentNode) {

        ChildNode childNode = (ChildNode) currentNode.getNodeDefinition();
        WorkflowStartRequest childStartRequest = buildChildWorkflowStartRequest(workflowStartRequest, childNode);
        if (childNode.getExecutionMode() == ExecutionMode.ASYNC) {
            invokeChildDontWaitForResults(childStartRequest);
        } else {
            // TODO: Implement synchronous child workflow invocation
            throw ApplicationFailure.newNonRetryableFailure("Sync mode for child workflow invocation is not yet implemented", "SYNC_MODE_NOT_IMPLEMENTED");
        }
    }

    private WorkflowStartRequest buildChildWorkflowStartRequest(WorkflowStartRequest parentStartRequest, ChildNode childNode) {
        WorkflowStartRequest childStartRequest = new WorkflowStartRequest();

        Map<String, Object> params = new HashMap<>();
        params.put(WORKFLOW_ID, childNode.getChildWorkflowId());
        params.put(VERSION, childNode.getChildWorkflowVersion());

        childStartRequest.setWorkflowId(generateChildWfId(parentStartRequest));
        childStartRequest.setParams(params);
        childStartRequest.setParentWorkflowId(parentStartRequest.getWorkflowId());
        childStartRequest.setIncidentId(parentStartRequest.getIncidentId());
        childStartRequest.setIssueDetail(parentStartRequest.getIssueDetail());
        childStartRequest.setCustomer(parentStartRequest.getCustomer());
        childStartRequest.setThreadContext(parentStartRequest.getThreadContext());
        childStartRequest.setOrderDetails(parentStartRequest.getOrderDetails());
        return childStartRequest;

    }

    private void invokeChildDontWaitForResults(WorkflowStartRequest childStartRequest) {
        ChildWorkflowOptions childWorkflowOptions = ChildWorkflowOptions.newBuilder().setWorkflowId(childStartRequest.getWorkflowId()).setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON).build();

        GenericWorkflow childWorkflow = io.temporal.workflow.Workflow.newChildWorkflowStub(GenericWorkflow.class, childWorkflowOptions);
        Async.procedure(childWorkflow::startWorkflow, childStartRequest);
        Promise<WorkflowExecution> childExecution = io.temporal.workflow.Workflow.getWorkflowExecution(childWorkflow);
        childExecution.get();

    }

}
