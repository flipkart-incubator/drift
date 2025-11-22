package com.drift.worker.activities;

import com.drift.commons.model.enums.WorkflowStatus;
import com.drift.commons.model.node.FailureNode;
import com.drift.worker.model.activity.ActivityRequest;
import com.drift.worker.model.activity.ActivityResponse;
import com.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;

import static com.drift.commons.utils.Constants.MAPPER;

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
