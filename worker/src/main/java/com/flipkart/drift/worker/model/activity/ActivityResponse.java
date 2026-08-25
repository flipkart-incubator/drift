package com.flipkart.drift.worker.model.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ActivityResponse implements Serializable {
    private WorkflowStatus workflowStatus;
    private JsonNode nodeResponse;
    private String nextNode;
    private String disposition;
    /** Populated by OnEventExecutor when expectedEventTypesVar or expectedEventTypes is set —
     *  carries the resolved event list back to the workflow thread via ActivityThinResponse. */
    private List<String> resolvedExpectedEventTypes;
}
