package com.drift.worker.activities;

import com.drift.commons.model.node.NodeDefinition;
import com.drift.commons.model.node.Workflow;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "fetchNodeDefinition")
public interface FetchNodeDefinitionAcitivity {
    NodeDefinition fetchNodeDefinition(String nodeName, String version);
}