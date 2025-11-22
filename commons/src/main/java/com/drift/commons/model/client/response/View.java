package com.drift.commons.model.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.drift.commons.model.instruction.Instruction;
import com.drift.commons.model.instruction.Option;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class View implements Serializable {
    private List<Option> inputOptions;
    private String layoutId;
}
