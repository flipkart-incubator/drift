package com.flipkart.drift.worker.bootstrap;

import com.flipkart.drift.persistence.cache.EntityVersionedCache;
import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.persistence.cache.WorkflowCache;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared DSL cache invalidation logic used by both {@link RedisCacheInvalidator}
 * (Redis pub-sub path) and {@link com.flipkart.drift.worker.task.CacheInvalidationTask}
 * (DNS-fanout HTTP path).
 */
@Slf4j
public class DslCacheManager {

    public static final String NODE_EVENT_ID = "NODE";
    public static final String WORKFLOW_EVENT_ID = "WORKFLOW";
    public static final String ALL = "ALL";

    private final NodeDefinitionCache nodeDefinitionCache;
    private final WorkflowCache workflowCache;

    @Inject
    public DslCacheManager(NodeDefinitionCache nodeDefinitionCache, WorkflowCache workflowCache) {
        this.nodeDefinitionCache = nodeDefinitionCache;
        this.workflowCache = workflowCache;
    }

    /**
     * Invalidates a single cache entry or the entire cache for the given type.
     *
     * @param type  "NODE" or "WORKFLOW"
     * @param key   HBase row key to invalidate, or "ALL" to invalidate the entire cache
     * @return true if the cache was found and invalidated, false if type is unknown
     */
    public boolean invalidate(String type, String key) {
        EntityVersionedCache<?> cache = getCache(type);
        if (cache == null) {
            log.warn("Unknown cache type: {}, ignoring invalidation for key {}", type, key);
            return false;
        }
        log.info("Invalidating cache: type={}, key={}", type, key);
        if (ALL.equalsIgnoreCase(key)) {
            cache.invalidateAll();
        } else {
            cache.invalidate(key);
        }
        return true;
    }

    private EntityVersionedCache<?> getCache(String type) {
        switch (type) {
            case NODE_EVENT_ID: return nodeDefinitionCache;
            case WORKFLOW_EVENT_ID: return workflowCache;
            default: return null;
        }
    }
}
