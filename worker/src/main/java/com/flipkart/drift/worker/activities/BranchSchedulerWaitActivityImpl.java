package com.flipkart.drift.worker.activities;

import com.flipkart.drift.sdk.spi.scheduler.SchedulerProvider;
import com.flipkart.drift.worker.Utility.SchedulerInitializer;
import com.flipkart.drift.worker.scheduler.SchedulerRegistrationUtil;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class BranchSchedulerWaitActivityImpl implements BranchSchedulerWaitActivity {

    private final SchedulerProvider schedulerProvider;

    @Inject
    public BranchSchedulerWaitActivityImpl(SchedulerInitializer schedulerInitializer) {
        this.schedulerProvider = schedulerInitializer.getProvider();
    }

    @Override
    public void registerSchedulerWait(String workflowId, String instanceName, long durationSeconds,
                                      Map<String, String> threadContext) {
        SchedulerRegistrationUtil.registerSchedulerWait(
                schedulerProvider, workflowId, instanceName, durationSeconds, threadContext);
    }
}
