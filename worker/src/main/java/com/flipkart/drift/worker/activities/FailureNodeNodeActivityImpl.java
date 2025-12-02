package com.flipkart.drift.worker.activities;

import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.node.FailureNode;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;

public class FailureNodeNodeActivityImpl extends BaseNodeActivityImpl<FailureNode> implements FailureNodeNodeActivity {

    @Inject
    public FailureNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        super(workflowContextHBService);
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<FailureNode> activityRequest) {
        return ActivityResponse.builder()
                .nodeResponse(MAPPER.createObjectNode().put("error", activityRequest.getNodeDefinition().getError()))
                .workflowStatus(WorkflowStatus.FAILED).build();
    }
}
