package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.WorkflowNodeType;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowNode {
    private String instanceName;
    private String resourceId;
    private String resourceVersion;
    private WorkflowNodeType type;
    private Map<String, String> parameters;
    private String contextOverrideKey;
    private String nextNode;
    private boolean end;
    NodeDefinition nodeDefinition; // This is populated while fetching WorkflowDefinition in FetchWorkflowActivity

    /**
     * Optional inline wait configuration. When present, WorkflowNodeExecutor applies ON_EVENT
     * wait behaviour after the node's own activity completes — without a separate WaitNode in the graph.
     * Null for nodes that do not need post-execution wait.
     */
    private WaitConfig waitConfig;
}
