package com.drift.worker.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.drift.commons.model.clientComponent.TransformerComponents;
import com.drift.commons.model.enums.WorkflowStatus;
import com.drift.commons.model.node.GroovyNode;
import com.drift.commons.model.resolvedDetails.TransformerDetails;
import com.drift.worker.model.activity.ActivityRequest;
import com.drift.worker.model.activity.ActivityResponse;
import com.drift.worker.service.WorkflowConfigStoreService;
import com.drift.worker.service.WorkflowContextHBService;
import com.drift.worker.translator.ClientResolvedDetailBuilder;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import static com.drift.commons.utils.Constants.MAPPER;
import static com.drift.commons.utils.Constants.Workflow.*;

@Slf4j
public class GroovyNodeNodeActivityImpl extends BaseNodeActivityImpl<GroovyNode> implements GroovyNodeNodeActivity {

    private final WorkflowConfigStoreService workflowConfigStoreService;
    @Inject
    public GroovyNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService, WorkflowConfigStoreService workflowConfigStoreService) {
        super(workflowContextHBService);
        this.workflowConfigStoreService = workflowConfigStoreService;
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<GroovyNode> activityRequest) {
        try {
            TransformerComponents components = new TransformerComponents(activityRequest.getNodeDefinition().getTransformer());
            ObjectNode contextWrapper = MAPPER.createObjectNode();
            contextWrapper.set(ENUM_STORE, MAPPER.valueToTree(workflowConfigStoreService.getEnumMapping()));
            TransformerDetails transformerDetails = ClientResolvedDetailBuilder
                    .evaluateGroovy(components, activityRequest.getNodeDefinition().getVersion(), contextWrapper.set(GLOBAL, activityRequest.getContext()), TransformerDetails.class);
            JsonNode groovyResponse = transformerDetails.getTransformedResponse();
            return ActivityResponse.builder()
                    .nodeResponse(groovyResponse)
                    .workflowStatus(WorkflowStatus.RUNNING).build();
        } catch (Exception e) {
            log.error("Exception while executing groovy node {}", e.getMessage());
            throw Activity.wrap(e);
        }
    }
}
