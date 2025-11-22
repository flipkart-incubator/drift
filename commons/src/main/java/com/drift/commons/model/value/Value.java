package com.drift.commons.model.value;

import com.fasterxml.jackson.annotation.*;
import com.drift.commons.model.enums.ValueType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "STRING", value = StringValue.class),
        @JsonSubTypes.Type(name = "MAP", value = MapValue.class),
        @JsonSubTypes.Type(name = "JSON", value = JsonValue.class),
        @JsonSubTypes.Type(name = "BOOLEAN", value = BooleanValue.class)
})
@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@NoArgsConstructor
public abstract class Value<T> {
    private T data;

    @JsonCreator
    protected Value(@JsonProperty("data") T data) {
        this.data = data;
    }

    @JsonIgnore
    public abstract ValueType getType();

}
