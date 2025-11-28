package com.flipkart.drift.worker.model.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.commons.model.client.response.View;
import com.flipkart.drift.commons.model.enums.WorkflowStatus;
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
