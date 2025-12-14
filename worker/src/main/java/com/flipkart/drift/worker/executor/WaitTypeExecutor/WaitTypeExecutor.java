package com.flipkart.drift.worker.executor.WaitTypeExecutor;

import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;

public interface WaitTypeExecutor {
    ActivityResponse executeWait(ActivityRequest<WaitNode> activityRequest) throws Exception;
}
