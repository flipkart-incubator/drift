package com.flipkart.drift.worker.model.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import lombok.*;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class WorkflowContext implements Serializable {
    private String workflowId;
    private ObjectNode context;
}
