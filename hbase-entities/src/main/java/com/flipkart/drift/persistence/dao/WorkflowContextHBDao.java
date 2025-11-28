package com.flipkart.drift.persistence.dao;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.google.inject.Inject;

import java.io.IOException;

public class WorkflowContextHBDao extends AbstractEntityDao<String, WorkflowContextHB, String> {
    @Inject
    public WorkflowContextHBDao(IConnectionProvider connectionProvider, ObjectMapper objectMapper) throws IOException {
        super(connectionProvider, connectionProvider.getConnection(ConnectionType.HOT), objectMapper);
    }
}

