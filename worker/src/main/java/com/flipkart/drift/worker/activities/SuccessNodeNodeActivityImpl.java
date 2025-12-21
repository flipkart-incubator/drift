package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.node.SuccessNode;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;

public class SuccessNodeNodeActivityImpl extends BaseNodeActivityImpl<SuccessNode> implements SuccessNodeNodeActivity {

    @Inject
    public SuccessNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        super(workflowContextHBService);
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<SuccessNode> activityRequest) {
        return ActivityResponse.builder()
                .nodeResponse(MAPPER.createObjectNode().put("comment", activityRequest.getNodeDefinition().getComment()))
                .workflowStatus(activityRequest.getNodeDefinition().getExecutionMode() == ExecutionMode.SYNC
                        ? WorkflowStatus.COMPLETED
                        : WorkflowStatus.ASYNC_COMPLETE).build();
    }
}
