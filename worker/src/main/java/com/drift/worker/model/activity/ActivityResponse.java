package com.drift.worker.model.activity;

import com.fasterxml.jackson.databind.JsonNode;
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
public class ActivityResponse implements Serializable {
    private WorkflowStatus workflowStatus;
    private JsonNode nodeResponse;
    private String nextNode;
    private String disposition;
}
