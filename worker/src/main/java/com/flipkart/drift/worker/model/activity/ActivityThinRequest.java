package com.flipkart.drift.worker.model.activity;

import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ActivityThinRequest<T extends NodeDefinition> {
    private String workflowId;
    private WorkflowNode workflowNode;
    private T nodeDefinition;
    private Map<String, String> threadContext;
}
