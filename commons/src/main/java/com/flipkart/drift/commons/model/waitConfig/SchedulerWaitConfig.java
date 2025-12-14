package com.flipkart.drift.commons.model.waitConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.WaitType;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;


@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchedulerWaitConfig extends WaitConfig {
    @Positive(message = "duration in second must be > 0")
    private long duration;
    @NotNull(message = "executionFlow can't be null")
    private ExecutionMode executionFlow;

    public SchedulerWaitConfig() {
        super(WaitType.SCHEDULER_WAIT);
    }

}
