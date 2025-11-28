package com.flipkart.drift.commons.model.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flipkart.drift.commons.model.enums.ValueType;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(callSuper = true)
@NoArgsConstructor
public class BooleanValue extends Value<Boolean> {

    @JsonCreator
    public BooleanValue(@JsonProperty("data") Boolean data) {
        super(data);
    }

    @Override
    public ValueType getType() {
        return ValueType.BOOLEAN;
    }
}
