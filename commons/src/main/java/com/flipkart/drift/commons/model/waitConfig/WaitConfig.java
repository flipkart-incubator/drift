package com.flipkart.drift.commons.model.waitConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flipkart.drift.commons.model.enums.WaitType;

import javax.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "waitType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SchedulerWaitConfig.class, name = "SCHEDULER_WAIT"),
        @JsonSubTypes.Type(value = AbsoluteWaitConfig.class, name = "ABSOLUTE_WAIT"),
        @JsonSubTypes.Type(value = OnEventConfig.class, name = "ON_EVENT")
})

public abstract class WaitConfig {


    @NotNull(message = "waitType can't be null")
    protected WaitType waitType;

    protected WaitConfig(WaitType waitType) {
        this.waitType = waitType;
    }

    @JsonIgnore
    public WaitType getWaitType() {
        return waitType;
    }
}
