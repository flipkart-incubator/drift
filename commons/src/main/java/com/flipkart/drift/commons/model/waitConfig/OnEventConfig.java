package com.flipkart.drift.commons.model.waitConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.WaitType;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnEventConfig extends WaitConfig {


    public OnEventConfig() {
        super(WaitType.ON_EVENT);
    }

    // Additional methods or validations can be added here if needed
}
