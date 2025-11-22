package com.drift.worker.activities;

import com.drift.commons.model.enums.WorkflowStatus;
import com.drift.commons.model.node.SuccessNode;
import com.drift.worker.model.activity.ActivityRequest;
import com.drift.worker.model.activity.ActivityResponse;
import com.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;

import static com.drift.commons.utils.Constants.MAPPER;

public class SuccessNodeNodeActivityImpl extends BaseNodeActivityImpl<SuccessNode> implements SuccessNodeNodeActivity {

    @Inject
    public SuccessNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        super(workflowContextHBService);
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<SuccessNode> activityRequest) {
        return ActivityResponse.builder()
                .nodeResponse(MAPPER.createObjectNode().put("comment", activityRequest.getNodeDefinition().getComment()))
                .workflowStatus(WorkflowStatus.COMPLETED).build();
    }
}
