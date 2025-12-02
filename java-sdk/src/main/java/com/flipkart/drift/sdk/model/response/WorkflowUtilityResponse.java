package com.flipkart.drift.sdk.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flipkart.drift.sdk.model.enums.WorkflowUtilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowUtilityResponse implements Serializable {
    private String workflowId;
    private String node;
    private WorkflowUtilityStatus status;
    private Object response;
}
