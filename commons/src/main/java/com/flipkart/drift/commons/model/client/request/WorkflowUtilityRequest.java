package com.flipkart.drift.commons.model.client.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowUtilityRequest extends WorkflowRequest {
    @NotNull(message = "node cannot be null")
    private String node;
    private Map<String, Object> parameters;
}
