package com.flipkart.drift.commons.model.instruction;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Option {
    private String id;
    private String parentId;
    private String description;
    private Tag tags;
    private String possibleDynamicValues; // for defining dynamic values
    private List<PossibleValue> possibleValues; // for defining first lvl static values
    private Map<String, Option> possibleDependentValues; // for defining inner lvl static values
    private List<Instruction> instructions;
}
