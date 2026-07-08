package com.flipkart.drift.worker.activities;

import io.temporal.activity.ActivityInterface;

import java.util.Map;

@ActivityInterface(namePrefix = "branchSchedulerWaitActivity")
public interface BranchSchedulerWaitActivity {
    void registerSchedulerWait(String workflowId, String instanceName, long durationSeconds,
                               Map<String, String> threadContext);
}
