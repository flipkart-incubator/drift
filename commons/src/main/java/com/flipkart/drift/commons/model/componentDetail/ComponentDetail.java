package com.flipkart.drift.commons.model.componentDetail;

import com.fasterxml.jackson.annotation.*;
import com.flipkart.drift.commons.model.enums.ComponentDetailType;
import com.flipkart.drift.commons.model.value.Value;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "SCRIPT", value = ScriptedComponentDetail.class),
        @JsonSubTypes.Type(name = "STATIC", value = StaticComponentDetail.class)
})
@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@NoArgsConstructor
public abstract class ComponentDetail<T extends Value, S> {

    private String type;
    private T value;
    private S dataTypeToBeMappedTo;

    @JsonCreator
    public ComponentDetail(@JsonProperty("value") T value) {
        this.value = value;
    }

    public abstract ComponentDetailType getType();

}
