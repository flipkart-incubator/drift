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
}
