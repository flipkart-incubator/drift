package com.flipkart.drift.persistence.cache;


import java.util.Optional;

public interface EntityVersionedCache<ENTITY> {
    void init();
    Optional<ENTITY> get(String entityId,String version,String tenant);
    void refresh(String entityId,String version,String tenant);
    void invalidate(String entityId,String version,String tenant);
    void invalidate(String rowKey);
    void invalidateAll();
}

