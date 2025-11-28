package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.worker.util.Constants;
import com.flipkart.drift.commons.model.client.request.WorkflowResumeRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowStartRequest;
import com.flipkart.drift.commons.model.client.request.WorkflowUtilityRequest;
import com.flipkart.drift.worker.model.workflow.WorkflowContext;
import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.commons.utils.ObjectMapperUtil;
import com.google.inject.Inject;
import groovy.util.logging.Slf4j;
import io.temporal.activity.Activity;

import java.util.Map;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;

@Slf4j
public class WorkflowContextManagerActivityImpl implements WorkflowContextManagerActivity {

    private final WorkflowContextHBService workflowContextHBService;

    @Inject
    public WorkflowContextManagerActivityImpl(WorkflowContextHBService workflowContextHBService) {
        this.workflowContextHBService = workflowContextHBService;
    }

    @Override
    public void persistWorkflowState(WorkflowStartRequest workflowStartRequest, String workflowId) {
        ObjectNode objectNode = ObjectMapperUtil.INSTANCE.getObjectNode(workflowStartRequest);
        ObjectNode globalParams = generateGlobalParams(
                workflowStartRequest.getThreadContext(),
                workflowStartRequest.getParams()
        );
        objectNode.set(Constants.GLOBAL_PARAMS, globalParams);
        workflowContextHBService.createEntity(WorkflowContext.builder()
                .context(objectNode)
                .workflowId(workflowId)
                .build(), workflowStartRequest.getThreadContext());
    }

    @Override
    public void resumeWorkflowState(WorkflowResumeRequest workflowResumeRequest, String currentNodeRef) {
        WorkflowContextHB workflowContextHB = workflowContextHBService.getEntityById(workflowResumeRequest.getWorkflowId(), workflowResumeRequest.getThreadContext());
        if (workflowContextHB == null) {
            throw Activity.wrap(new RuntimeException("Workflow context not found for workflowId: " + workflowResumeRequest.getWorkflowId()));
        }
        ObjectNode contextNode = workflowContextHB.getContext();
        String viewResponseKey = currentNodeRef + Constants.VIEW_RESPONSE_SUFFIX;
        JsonNode viewResponse = ObjectMapperUtil.INSTANCE.getJsonNode(workflowResumeRequest.getViewResponse());
        contextNode.set(viewResponseKey, viewResponse);
        ObjectNode globalParams = generateGlobalParams(
                workflowResumeRequest.getThreadContext(),
                workflowResumeRequest.getParams()
        );
        contextNode.set(Constants.GLOBAL_PARAMS, globalParams);
        workflowContextHBService.updateEntity(WorkflowContext.builder()
                .context(contextNode)
                .workflowId(workflowResumeRequest.getWorkflowId())
                .build(), workflowResumeRequest.getThreadContext());
    }

    private ObjectNode generateGlobalParams(Map<String, String> threadContext, Map<String, Object> params) {
        ObjectNode globalParams = ObjectMapperUtil.INSTANCE.getMapper().createObjectNode();

        if (threadContext != null && !threadContext.isEmpty()) {
            ObjectNode threadContextNode = ObjectMapperUtil.INSTANCE.getObjectNode(threadContext);
            globalParams.set(Constants.THREAD_CONTEXT, threadContextNode);
        }

        if (params != null && !params.isEmpty()) {
            ObjectNode paramsNode = ObjectMapperUtil.INSTANCE.getObjectNode(params);
            globalParams.set(Constants.PARAMS, paramsNode);
        }

        return globalParams;
    }

    @Override
    public void disconnectedNodeState(WorkflowUtilityRequest workflowUtilityRequest, String workflowId) {
        WorkflowContextHB workflowContextHB = workflowContextHBService.getEntityById(workflowId, workflowUtilityRequest.getThreadContext());
        if (workflowContextHB == null) {
            throw Activity.wrap(new RuntimeException("Workflow context not found for workflowId: " + workflowId));
        }
        ObjectNode contextWrapper = workflowUtilityRequest.getParameters() != null ?
                MAPPER.valueToTree(workflowUtilityRequest.getParameters()) : MAPPER.createObjectNode();
        workflowContextHBService.updateEntity(WorkflowContext.builder()
                .context(workflowContextHB.getContext().set(workflowUtilityRequest.getNode(), contextWrapper))
                .workflowId(workflowId)
                .build(), workflowUtilityRequest.getThreadContext());
    }
}