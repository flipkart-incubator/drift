package com.drift.worker.model.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.drift.commons.model.node.NodeDefinition;
import lombok.*;

import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ActivityRequest<T extends NodeDefinition> {
    private String workflowId;
    private T nodeDefinition;
    private JsonNode context;
    private Map<String, Object> nodeParameters;
    private Boolean isTerminal;
    private Map<String, String> threadContext;
}
