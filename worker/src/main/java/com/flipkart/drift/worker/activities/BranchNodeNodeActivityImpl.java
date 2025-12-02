package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.commons.model.clientComponent.BranchComponents;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.node.BranchNode;
import com.flipkart.drift.commons.model.resolvedDetails.BranchDetails;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.worker.translator.ClientResolvedDetailBuilder;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;
import static com.flipkart.drift.commons.utils.Constants.Workflow.GLOBAL;

@Slf4j
public class BranchNodeNodeActivityImpl extends BaseNodeActivityImpl<BranchNode> implements BranchNodeNodeActivity {

    @Inject
    public BranchNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        super(workflowContextHBService);
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<BranchNode> activityRequest) {
        try {
            ObjectNode branchResponse = MAPPER.createObjectNode();
            for (BranchComponents choice : activityRequest.getNodeDefinition().getChoices()) {
                ObjectNode contextWrapper = MAPPER.createObjectNode();
                BranchDetails branchDetails = ClientResolvedDetailBuilder
                        .evaluateGroovy(choice, activityRequest.getNodeDefinition().getVersion(), contextWrapper.set(GLOBAL, activityRequest.getContext()), BranchDetails.class);
                branchResponse.put("nextNode", branchDetails.getNextNode());
                if (branchDetails.getRule()) {
                    return ActivityResponse.builder()
                            .nodeResponse(branchResponse)
                            .workflowStatus(WorkflowStatus.RUNNING)
                            .nextNode(branchDetails.getNextNode()).build();
                }
            }
            branchResponse.put("nextNode", activityRequest.getNodeDefinition().getDefaultNode());
            return ActivityResponse.builder()
                    .nodeResponse(branchResponse)
                    .workflowStatus(WorkflowStatus.RUNNING)
                    .nextNode(activityRequest.getNodeDefinition().getDefaultNode()).build();
        } catch (Exception e) {
            log.error("Exception while executing branch node {}", e.getMessage());
            throw Activity.wrap(e);
        }
    }
}
