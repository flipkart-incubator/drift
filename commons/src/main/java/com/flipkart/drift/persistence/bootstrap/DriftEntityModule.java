package com.flipkart.drift.persistence.bootstrap;

import com.codahale.metrics.InstrumentedExecutorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.persistence.cache.WorkflowCache;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.NodeDefinitionDao;
import com.flipkart.drift.persistence.dao.WorkflowDefinitionDao;
import com.flipkart.drift.persistence.entity.NodeHB;
import com.flipkart.drift.persistence.entity.WorkflowHB;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.Workflow;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DriftEntityModule extends AbstractModule {
    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    public NodeDefinitionCache getNodeDefinitionCache(Provider<NodeDefinitionDao> nodeDefinitionDao, Provider<StaticCacheRefreshConfig> staticCacheRefreshConfig,
                                                      Provider<CacheMaxEntriesConfig> maxEntriesConfig,InstrumentedExecutorService instrumentedExecutorService,
                                                      Provider<ObjectMapper> objectMapperProvider) {
        NodeDefinitionCache nodeDefinitionCache = new NodeDefinitionCache(staticCacheRefreshConfig.get()
                                                                                  .getNodeDefinitionConfig(),
                                                                          maxEntriesConfig.get()
                                                                                  .getNodeDefinitionConfig(), instrumentedExecutorService,
                                                                          objectMapperProvider.get(),
                                                                          nodeDefinitionDao.get());
        log.info("Loading NodeDefinition in to cache");
        nodeDefinitionCache.init();
        try {
            List<NodeHB> nodeHBs = nodeDefinitionDao.get().findAll("main", Optional.empty(), ConnectionType.HOT);
            Map<String, NodeDefinition> nodeDefinitionMap = nodeHBs.stream().collect(Collectors.toMap(NodeHB::composeRowKey, x -> nodeDefinitionCache.mapDbtoAppEntity(x)));
            nodeDefinitionCache.getCache().putAll(nodeDefinitionMap);
        } catch (IOException e) {
            throw new ApiException("loading node definition cache failed");
        }
        return nodeDefinitionCache;
    }

    @Provides
    @Singleton
    public WorkflowCache getWorkflowCache(Provider<WorkflowDefinitionDao> workflowDefinitionDaoProvider, Provider<StaticCacheRefreshConfig> staticCacheRefreshConfig,Provider<CacheMaxEntriesConfig> maxEntriesConfig, InstrumentedExecutorService instrumentedExecutorService,
                                          Provider<ObjectMapper> objectMapperProvider) {
        WorkflowCache workflowCache = new WorkflowCache(staticCacheRefreshConfig.get().getWorkflowConfig(),
                                                        maxEntriesConfig.get().getWorkflowConfig(),
                                                        instrumentedExecutorService,
                                                        objectMapperProvider.get(),
                                                        workflowDefinitionDaoProvider.get());
        log.info("Loading WorkflowDefinition in to cache");
        workflowCache.init();
        try {
            List<WorkflowHB> workflowHBList = workflowDefinitionDaoProvider.get().findAll("main", Optional.empty(), ConnectionType.HOT);
            Map<String, Workflow> workflowMap = workflowHBList.stream().collect(Collectors.toMap(WorkflowHB::composeRowKey, x -> workflowCache.mapDbtoAppEntity(x)));
            workflowCache.getCache().putAll(workflowMap);
        } catch (IOException e) {
            throw new ApiException("loading workflow definition cache failed");
        }
        return workflowCache;
    }
}

