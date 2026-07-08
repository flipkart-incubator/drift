package com.flipkart.drift.sdk.model.client;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class ScheduleRequest {
    private long scheduleTimeInMillis;
    private String workflowId;
    /** Node instance that registered the wait; used by scheduler callback for parallel workflows. */
    private String instanceName;
    /**
     * Event type the scheduler must send on {@code resumeWorkflow} when the timer fires.
     * For parallel SCHEDULER_WAIT branches: {@code SCHEDULER_WAIT_{instanceName}}.
     */
    private String eventType;
}
