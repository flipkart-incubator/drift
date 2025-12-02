package com.flipkart.drift.sdk.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class WorkflowRequest {
    private String incidentId;
    private String workflowId;
    private Map<String, String> threadContext;
    private Map<String, Object> params;
}
