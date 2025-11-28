package com.flipkart.drift.commons.model.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.drift.commons.model.enums.ValueType;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(callSuper = true)
@NoArgsConstructor
public class JsonValue extends Value<JsonNode> {

    @JsonCreator
    public JsonValue(@JsonProperty("data") JsonNode data) {
        super(data);
    }

    @Override
    public ValueType getType() {
        return ValueType.JSON;
    }

}
