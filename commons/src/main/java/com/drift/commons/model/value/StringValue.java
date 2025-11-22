package com.drift.commons.model.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.drift.commons.model.enums.ValueType;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(callSuper = true)
@NoArgsConstructor
public class StringValue extends Value<String> {

    @JsonCreator
    public StringValue(@JsonProperty("data") String data) {
        super(data);
    }

    @Override
    public ValueType getType() {
        return ValueType.STRING;
    }

}
