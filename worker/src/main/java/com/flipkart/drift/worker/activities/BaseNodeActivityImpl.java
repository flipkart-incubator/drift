package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.worker.Utility.NodeParameterEvaluator;
import com.flipkart.drift.worker.model.activity.ActivityThinRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;
import com.flipkart.drift.worker.executor.WaitTypeExecutor.OnEventExecutor;
import com.flipkart.drift.worker.model.workflow.WorkflowContext;
import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.commons.utils.ObjectMapperUtil;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class BaseNodeActivityImpl<T extends NodeDefinition> implements INodeActivity<T> {

    private final WorkflowContextHBService workflowContextHBService;

    protected BaseNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        this.workflowContextHBService = workflowContextHBService;
    }

    // Wrapper method for context management
    public final ActivityThinResponse execute(ActivityThinRequest<T> activityThinRequest) {
        // Execute common steps 1-3
        ActivityResponse response = executeWithContextManagement(activityThinRequest);

        // Step 6: Handle special response types
        View currentView = null;
        String disposition = null;
        if (NodeType.INSTRUCTION.equals(activityThinRequest.getNodeDefinition().getType())) {
            try {
                currentView = ObjectMapperUtil.INSTANCE.getObj(response.getNodeResponse().toString(), View.class);
                disposition = response.getDisposition();
            } catch (IOException e) {
                throw Activity.wrap(new RuntimeException("Error while creating response view for workflowId: " + activityThinRequest.getWorkflowId() + " Node id: " + activityThinRequest.getNodeDefinition().getId()));
            }
        }

        // Step 7: Return thin response
        return ActivityThinResponse.builder()
                .workflowStatus(response.getWorkflowStatus())
                .nextNode(response.getNextNode())
                .disposition(disposition)
                .view(currentView)
                .resolvedExpectedEventTypes(response.getResolvedExpectedEventTypes())
                .build();
    }

    // Fat response wrapper method that returns complete ActivityResponse
    public final ActivityResponse executeWithFatResponse(ActivityThinRequest<T> activityThinRequest) {
        // Execute common steps 1-5 and return fat response
        return executeWithContextManagement(activityThinRequest);
    }

    // Private helper method containing common steps 1-3
    private ActivityResponse executeWithContextManagement(ActivityThinRequest<T> activityThinRequest) {
        String workflowId = activityThinRequest.getWorkflowId();

        // Step 1: Fetch context from HBase
        WorkflowContextHB context = workflowContextHBService.getEntityById(workflowId, activityThinRequest.getThreadContext());
        if (context == null) {
            throw Activity.wrap(new RuntimeException("Workflow context not found for ID: " + workflowId));
        }

        ActivityRequest<T> activityRequest = new ActivityRequest<>();
        activityRequest.setIsTerminal(activityThinRequest.getWorkflowNode().isEnd());
        activityRequest.setWorkflowId(workflowId);
        activityRequest.setThreadContext(activityThinRequest.getThreadContext());
        updateContextWithNodeParameters(context.getContext(), activityThinRequest.getWorkflowNode().getParameters());
        activityRequest.setContext(context.getContext());
        activityRequest.setNodeDefinition(activityThinRequest.getNodeDefinition());
        // Step 2: Execute the actual logic
        ActivityResponse response = executeNode(activityRequest);

        // Step 2a: If the WorkflowNode has an inline waitConfig with expectedEventTypesVar,
        // resolve the context variable here while we still have the HBase context.
        resolveInlineWaitConfigVar(activityThinRequest.getWorkflowNode(), activityRequest, response);

        // Step 2b: If node is marked as terminal (end=true) and the node's own status is still
        // transitional (RUNNING or WAITING), override to COMPLETED so all node types correctly
        // terminate the workflow. FAILED, COMPLETED, and ASYNC_COMPLETE are intentional and preserved.
        if (Boolean.TRUE.equals(activityRequest.getIsTerminal())
                && response.getWorkflowStatus() != WorkflowStatus.FAILED
                && response.getWorkflowStatus() != WorkflowStatus.COMPLETED
                && response.getWorkflowStatus() != WorkflowStatus.ASYNC_COMPLETE) {
            response.setWorkflowStatus(WorkflowStatus.COMPLETED);
        }

        // Step 3: Persist updated context
        workflowContextHBService.updateEntity(WorkflowContext.builder()
                .workflowId(workflowId)
                .context(context.getContext().putPOJO(generateNodeIdentifier(activityThinRequest.getWorkflowNode()), response.getNodeResponse()))
                .build(), activityThinRequest.getThreadContext());

        return response;
    }

    private void updateContextWithNodeParameters(ObjectNode context,
                                                 Map<String, String> parameters) {
        ObjectNode nodeParameters = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);
        context.set("nodeParameters", nodeParameters);
    }

    private String generateNodeIdentifier(WorkflowNode currentNode) {
        if (currentNode.getContextOverrideKey() != null) {
            return currentNode.getContextOverrideKey();
        }
        return currentNode.getInstanceName();
    }

    private void resolveInlineWaitConfigVar(WorkflowNode workflowNode, ActivityRequest<T> activityRequest, ActivityResponse response) {
        WaitConfig inlineWait = workflowNode.getWaitConfig();
        if (inlineWait == null || WaitType.ON_EVENT != inlineWait.getWaitType()) {
            return;
        }
        OnEventConfig config = (OnEventConfig) inlineWait;
        if (config.getExpectedEventTypesVar() == null || config.getExpectedEventTypesVar().isEmpty()) {
            return;
        }
        List<String> resolved = OnEventExecutor.resolveFromContext(
                config.getExpectedEventTypesVar(), activityRequest.getContext(), activityRequest.getWorkflowId());
        log.info("WfId: {} inline waitConfig var '{}' resolved {} event types",
                activityRequest.getWorkflowId(), config.getExpectedEventTypesVar(), resolved.size());
        response.setResolvedExpectedEventTypes(resolved);
    }
}