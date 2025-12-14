package com.flipkart.drift.worker.activities;


import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.worker.executor.WaitTypeExecutor.WaitTypeExecutor;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class WaitNodeNodeActivityImpl extends BaseNodeActivityImpl<WaitNode> implements WaitNodeNodeActivity {


    private final Map<WaitType, WaitTypeExecutor> waitTypeExecutorMap;

    @Inject
    public WaitNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService, Map<WaitType, WaitTypeExecutor> waitTypeExecutorMap) {
        super(workflowContextHBService);
        this.waitTypeExecutorMap = waitTypeExecutorMap;
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<WaitNode> activityRequest) {
        try {
            WaitType waitType = activityRequest.getNodeDefinition().getWaitType();
            WaitTypeExecutor waitExecutor = waitTypeExecutorMap.get(waitType);
            return waitExecutor.executeWait(activityRequest);
        } catch (Exception e) {
            log.error("Exception while executing Wait node", e);
            throw Activity.wrap(e);
        }
    }
}
