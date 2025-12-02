package com.flipkart.drift.persistence.cache;

import com.codahale.metrics.InstrumentedExecutorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.NodeDefinitionDao;
import com.flipkart.drift.persistence.entity.NodeHB;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.utils.Utility;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NodeDefinitionCache extends AbstractEntityVersionedCache<NodeDefinition, NodeHB> {
    private final ObjectMapper objectMapper;
    private final NodeDefinitionDao nodeDefinitionDao;

    @Inject
    public NodeDefinitionCache(Long refreshTime,
                               Long maxEntires,
                               InstrumentedExecutorService instrumentedExecutorService,
                               ObjectMapper objectMapper,
                               NodeDefinitionDao nodeDefinitionDao) {
        super(refreshTime,
              maxEntires,
              instrumentedExecutorService);
        this.objectMapper = objectMapper;
        this.nodeDefinitionDao = nodeDefinitionDao;
    }

    @Override
    public NodeHB getEntityFromDb(String rowKey) throws Exception {
        return nodeDefinitionDao.get(rowKey, ConnectionType.HOT);
    }

    @Override
    public NodeDefinition mapDbtoAppEntity(NodeHB nodeHB) {
        try {
            if (nodeHB == null) return null;
            return objectMapper.convertValue(nodeHB.getNodeData(), NodeDefinition.class);
        } catch (Exception e) {
            log.error("Error in mapping NodeHB to NodeDefinition", e);
            return null;
        }
    }

    @Override
    public String getRowKey(String entityId,
                               String version,
                               String tenant) {
        return Utility.generateRowKey(entityId, version);
    }
}

