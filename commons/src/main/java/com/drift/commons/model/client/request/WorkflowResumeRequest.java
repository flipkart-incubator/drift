package com.drift.commons.model.client.request;

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
public class WorkflowResumeRequest extends WorkflowRequest {
    @NotNull(message = "viewRequest cannot be null")
    private ViewResponse viewResponse;
}