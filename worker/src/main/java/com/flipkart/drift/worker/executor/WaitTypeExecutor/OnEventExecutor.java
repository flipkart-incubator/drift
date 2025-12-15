package com.flipkart.drift.worker.executor.WaitTypeExecutor;

import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;

public class OnEventExecutor implements WaitTypeExecutor {


    @Override
    public ActivityResponse executeWait(ActivityRequest<WaitNode> activityRequest) throws Exception {
        // This method is intended to handle on-event wait logic. future useCase
        throw new UnsupportedOperationException("ON_EVENT wait type is not yet implemented");
    }
}
