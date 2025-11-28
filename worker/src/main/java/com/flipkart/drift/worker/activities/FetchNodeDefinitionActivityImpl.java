package com.flipkart.drift.worker.activities;

import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.google.inject.Inject;

public class FetchNodeDefinitionActivityImpl implements FetchNodeDefinitionAcitivity {
    private final NodeDefinitionCache nodeDefinitionCache;
    @Inject
    public FetchNodeDefinitionActivityImpl(NodeDefinitionCache nodeDefinitionCache) {
        this.nodeDefinitionCache = nodeDefinitionCache;
    }

    @Override
    public NodeDefinition fetchNodeDefinition(String nodeName,
                                              String version) {
        // TODO : check where to get tenant from
        return nodeDefinitionCache.get(nodeName, version,
                                       "fk").get();
    }
}
