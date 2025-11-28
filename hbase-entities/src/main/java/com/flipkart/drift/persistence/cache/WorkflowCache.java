package com.flipkart.drift.persistence.cache;

import com.codahale.metrics.InstrumentedExecutorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.WorkflowDefinitionDao;
import com.flipkart.drift.persistence.entity.WorkflowHB;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.utils.Utility;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowCache extends AbstractEntityVersionedCache<Workflow, WorkflowHB> {
    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionDao workflowDefinitionDao;

    @Inject
    public WorkflowCache(Long refreshTime,
                         Long maxEntries,
                         InstrumentedExecutorService instrumentedExecutorService,
                         ObjectMapper objectMapper,
                         WorkflowDefinitionDao workflowDefinitionDao) {
        super(refreshTime,
              maxEntries,
              instrumentedExecutorService);
        this.objectMapper = objectMapper;
        this.workflowDefinitionDao = workflowDefinitionDao;
    }

    @Override
    public WorkflowHB getEntityFromDb(String rowKey) throws Exception {
        return workflowDefinitionDao.get(rowKey, ConnectionType.HOT);
    }

    @Override
    public Workflow mapDbtoAppEntity(WorkflowHB workflowHB) {
        try {
            return objectMapper.convertValue(workflowHB.getWorkflowData(), Workflow.class);
        } catch (Exception e) {
            log.error("Error in mapping NodeHB to Workflow", e);
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

