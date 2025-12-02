package com.flipkart.drift.sdk.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.sdk.model.response.ViewResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotNull;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowResumeRequest extends WorkflowRequest {
    @NotNull(message = "viewRequest cannot be null")
    private ViewResponse viewResponse;
}