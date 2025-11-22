package com.drift.worker.activities;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "returnControlActivity")
public interface ReturnControlActivity {
    Long exec(String workflowId);
}
