package com.flipkart.drift.worker.scheduler;

import com.flipkart.drift.commons.parallel.ParallelWaitConditions;
import com.flipkart.drift.sdk.model.client.ScheduleRequest;
import com.flipkart.drift.sdk.spi.scheduler.SchedulerProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class SchedulerRegistrationUtil {

    private SchedulerRegistrationUtil() {
    }

    public static void registerSchedulerWait(SchedulerProvider schedulerProvider,
                                             String workflowId,
                                             String instanceName,
                                             long durationSeconds,
                                             Map<String, String> threadContext) {
        long scheduleTimeMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationSeconds);
        String eventType = ParallelWaitConditions.schedulerEventType(instanceName);

        ScheduleRequest schedulerData = ScheduleRequest.builder()
                .scheduleTimeInMillis(scheduleTimeMillis)
                .workflowId(workflowId)
                .instanceName(instanceName)
                .eventType(eventType)
                .build();

        String perfFlagStr = threadContext != null ? threadContext.get("perfFlag") : null;
        boolean perfFlag = "true".equalsIgnoreCase(perfFlagStr);

        if (!perfFlag) {
            schedulerProvider.addSchedule(schedulerData);
            log.info("Registered scheduler wait for wfId={} node={} eventType={} at {}",
                    workflowId, instanceName, eventType, scheduleTimeMillis);
        }
    }
}
