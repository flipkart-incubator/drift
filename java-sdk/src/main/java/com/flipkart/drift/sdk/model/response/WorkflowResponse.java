package com.flipkart.drift.sdk.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
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
public class WorkflowResponse implements Serializable {
    private String incidentId;
    private String workflowId;
    private WorkflowStatus workflowStatus;
    private String disposition;
    private String errorMessage;
    private View view;
}
