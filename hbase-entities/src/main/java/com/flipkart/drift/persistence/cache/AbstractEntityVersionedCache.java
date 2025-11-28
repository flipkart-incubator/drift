package com.flipkart.drift.persistence.cache;

import com.codahale.metrics.InstrumentedExecutorService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractEntityVersionedCache<APP_ENTITY, DB_ENTITY> implements EntityVersionedCache<APP_ENTITY> {
    @Getter
    private LoadingCache<String, APP_ENTITY> cache;
    private final Long refreshTime;
    private final Long maxEntries;
    private final InstrumentedExecutorService instrumentedExecutorService;

    public AbstractEntityVersionedCache(Long refreshTime,
                                        Long maxEntries, InstrumentedExecutorService instrumentedExecutorService) {
        this.refreshTime = refreshTime;
        this.maxEntries = maxEntries;
        this.instrumentedExecutorService = instrumentedExecutorService;
    }

    @Override
    public void init() {
        CacheLoader<String, APP_ENTITY> loader = new CacheLoader<>() {
            @Override
            public APP_ENTITY load(String rowKey) throws Exception {
                DB_ENTITY dbEntity = getEntityFromDb(rowKey);
                if (dbEntity == null) {
                    throw new Exception("Cache load failed for key: " + rowKey + " as entity is not found in DB.");
                }
                return mapDbtoAppEntity(dbEntity);
            }

            @Override
            public ListenableFuture<APP_ENTITY> reload(String key, APP_ENTITY oldValue) throws Exception {
                ListenableFutureTask<APP_ENTITY> task = ListenableFutureTask.create(new Callable<APP_ENTITY>() {
                    public APP_ENTITY call() throws Exception {
                        return load(key);
                    }
                });
                instrumentedExecutorService.execute(task);
                return task;
            }
        };

        CacheBuilder builder = CacheBuilder.newBuilder()
            .refreshAfterWrite(refreshTime, TimeUnit.MINUTES);
        if (maxEntries != null) {
            builder.maximumSize(maxEntries);
        }
        cache = builder.build(loader);
    }

    @Override
    public Optional<APP_ENTITY> get(String entityId, String version, String tenant) {
        String rowKey = getRowKey(entityId, version, tenant);
        try {
            return Optional.ofNullable(cache.get(rowKey));
        } catch (ExecutionException e) {
            log.error("Error while fetching entity from cache", e);
            return Optional.empty();
        }
    }

    @Override
    public void refresh(String entityId, String version, String tenant) {
        String rowKey = getRowKey(entityId, version, tenant);
        cache.refresh(rowKey);
    }

    @Override
    public void invalidate(String entityId, String version, String tenant) {
        String rowKey = getRowKey(entityId, version, tenant);
        cache.invalidate(rowKey);
    }

    @Override
    public void invalidate(String rowKey) {
        cache.invalidate(rowKey);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    public abstract DB_ENTITY getEntityFromDb(String rowKey) throws Exception;
    public abstract APP_ENTITY mapDbtoAppEntity(DB_ENTITY dbEntity);
    public abstract String getRowKey(String entityId, String version, String tenant);

}

