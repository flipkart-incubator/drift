package com.drift.persistence.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.drift.persistence.entity.WorkflowHB;
import com.google.inject.Inject;

import java.io.IOException;

public class WorkflowDefinitionDao extends AbstractEntityDao<String, WorkflowHB, String> {
    @Inject
    public WorkflowDefinitionDao(IConnectionProvider connectionProvider, ObjectMapper objectMapper) throws IOException {
        super(connectionProvider, connectionProvider.getConnection(ConnectionType.HOT), objectMapper);
    }
}

