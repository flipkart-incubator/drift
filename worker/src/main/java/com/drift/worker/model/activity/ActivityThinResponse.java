package com.drift.worker.model.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.drift.commons.model.client.response.View;
import com.drift.commons.model.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ActivityThinResponse implements Serializable {
    private WorkflowStatus workflowStatus;
    private String nextNode;
    private JsonNode errorResponse;
    private View view;
    private String disposition;
}
