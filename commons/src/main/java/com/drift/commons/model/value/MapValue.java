package com.drift.commons.model.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.drift.commons.model.enums.ValueType;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

@ToString(callSuper = true)
@NoArgsConstructor
public class MapValue extends Value<Map<String, Object>> {

    @JsonCreator
    public MapValue(@JsonProperty("data") Map<String, Object> data) {
        super(data);
    }

    @Override
    public ValueType getType() {
        return ValueType.MAP;
    }

}
