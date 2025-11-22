package com.drift.worker.activities;


import com.drift.worker.model.activity.ActivityThinRequest;
import com.drift.worker.model.activity.ActivityThinResponse;
import com.drift.commons.model.node.NodeDefinition;
import com.drift.worker.model.activity.ActivityRequest;
import com.drift.worker.model.activity.ActivityResponse;

public interface INodeActivity<T extends NodeDefinition> {
    ActivityResponse executeNode(ActivityRequest<T> activityRequest);
    ActivityThinResponse execute(ActivityThinRequest<T> activityThinRequest);
    ActivityResponse executeWithFatResponse(ActivityThinRequest<T> activityThinRequest);
}
