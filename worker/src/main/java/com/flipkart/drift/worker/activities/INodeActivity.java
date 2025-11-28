package com.flipkart.drift.worker.activities;


import com.flipkart.drift.worker.model.activity.ActivityThinRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;

public interface INodeActivity<T extends NodeDefinition> {
    ActivityResponse executeNode(ActivityRequest<T> activityRequest);
    ActivityThinResponse execute(ActivityThinRequest<T> activityThinRequest);
    ActivityResponse executeWithFatResponse(ActivityThinRequest<T> activityThinRequest);
}
