package com.flipkart.drift.sdk.spi.scheduler;


import com.flipkart.drift.sdk.model.client.ScheduleRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoOpSchedulerProvider implements SchedulerProvider {
    private boolean initialized = false;

    @Override
    public void init() {
        log.warn("NoOpSchedulerProvider initialized — scheduling is disabled; all scheduling requests will be ignored");
        initialized = true;
    }

    @Override
    public void addSchedule(ScheduleRequest request) {
        log.debug("NoOpSchedulerProvider: schedule at {} for workflowId={} instance={} eventType={}",
                request.getScheduleTimeInMillis(), request.getWorkflowId(),
                request.getInstanceName(), request.getEventType());
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

}
