package com.flipkart.drift.commons.model.componentDetail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flipkart.drift.commons.model.enums.ComponentDetailType;
import com.flipkart.drift.commons.model.value.StringValue;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Getter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptedComponentDetail extends ComponentDetail<StringValue, String> {

    @Builder
    @JsonCreator
    public ScriptedComponentDetail(@JsonProperty("value") StringValue value) {
        super(value);
    }

    @Override
    public ComponentDetailType getType() {
        return ComponentDetailType.SCRIPT;
    }
}
