package com.flipkart.drift.sdk.spi.scheduler;

import com.flipkart.drift.sdk.model.client.ScheduleRequest;

public interface SchedulerProvider {
    void init();

    void addSchedule(ScheduleRequest request);

    boolean isInitialized();
}
