package com.flipkart.drift.commons.model.resolvedDetails;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class BranchDetails implements ResolvedDetails {
    private final Boolean rule;
    private final String nextNode;

    @Builder
    @JsonCreator
    public BranchDetails(@JsonProperty("rule") Boolean rule,
                         @JsonProperty("nextNode") String nextNode) {
        this.rule = rule;
        this.nextNode = nextNode;
    }
}



