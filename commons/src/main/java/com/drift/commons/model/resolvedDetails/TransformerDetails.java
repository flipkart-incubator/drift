package com.drift.commons.model.resolvedDetails;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class TransformerDetails implements ResolvedDetails {
    private final JsonNode transformedResponse;

    @Builder
    @JsonCreator
    public TransformerDetails(@JsonProperty("transformer") JsonNode transformedResponse) {
        this.transformedResponse = transformedResponse;
    }


}
