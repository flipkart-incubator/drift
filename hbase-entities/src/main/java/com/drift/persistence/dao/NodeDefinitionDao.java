package com.drift.persistence.dao;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.drift.persistence.entity.NodeHB;
import com.google.inject.Inject;

import java.io.IOException;

public class NodeDefinitionDao extends AbstractEntityDao<String, NodeHB, String> {
    @Inject
    public NodeDefinitionDao(IConnectionProvider connectionProvider, ObjectMapper objectMapper) throws IOException {
        super(connectionProvider, connectionProvider.getConnection(ConnectionType.HOT), objectMapper);
    }
}

