package com.drift.commons.model.resolvedDetails;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class AttributeDetails implements ResolvedDetails {
    private final String attribute;

    @Builder
    @JsonCreator
    public AttributeDetails(@JsonProperty("attribute") String attribute) {
        this.attribute = attribute;
    }
}



