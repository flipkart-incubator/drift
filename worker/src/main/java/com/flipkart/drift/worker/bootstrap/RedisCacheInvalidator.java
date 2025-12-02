package com.flipkart.drift.worker.bootstrap;

import com.flipkart.drift.persistence.cache.EntityVersionedCache;
import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.persistence.cache.WorkflowCache;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolAbstract;
import redis.clients.jedis.JedisPubSub;
import com.google.inject.*;
import java.util.concurrent.Executors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.flipkart.drift.commons.utils.Constants.Workflow.DSL_UPDATE_CHANNEL;

@Slf4j
public class RedisCacheInvalidator implements Managed {
    public static final String NODE_EVENT_ID = "NODE";
    public static final String WORKFLOW_EVENT_ID = "WORKFLOW";

    private final NodeDefinitionCache nodeDefinitionCache;
    private final WorkflowCache workflowCache;
    private final JedisPoolAbstract jedisPool;
    private final ExecutorService executorService;
    private volatile boolean running = false;  
    
    @Inject
    public RedisCacheInvalidator(NodeDefinitionCache nodeDefinitionCache,
                                 WorkflowCache workflowCache,
                                 JedisPoolAbstract jedisPool) {
        this.nodeDefinitionCache = nodeDefinitionCache;
        this.workflowCache = workflowCache;
        this.jedisPool = jedisPool;
        this.executorService = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("redis-subscriber-%d")
                        .setDaemon(true)
                        .build()
        );
    }

    @Override
    public void start() throws Exception {
        try {
            log.info("Starting Redis cache invalidation listener");
            running = true;
            executorService.submit(() -> {
                while (running) {
                    try (Jedis jedis = jedisPool.getResource()) {
                        log.info("Subscribing to cache invalidation channel");
                        jedis.subscribe(new JedisPubSub() {
                            @Override
                            public void onMessage(String channel, String message) {
                                try {
                                    log.info("Received cache invalidation message: {}", message);
                                /*
                                    message is of format : NODE <rowKey> or NODE ALL or WORKFLOW <rowKey> or WORKFLOW ALL
                                */
                                    String[] parts = message.split(" ", 2);
                                    EntityVersionedCache<?> cache = getCache(parts[0]);
                                    if (cache != null) {
                                        if (parts[1].equals("ALL")) {
                                            cache.invalidateAll();
                                        } else {
                                            cache.invalidate(parts[1]);
                                        }
                                    } else {
                                        log.warn("Unknown cache type: {}, ignoring msg {}", parts[0], message);
                                    }
                                } catch (Exception e) {
                                    log.error("Error processing cache invalidation message", e);
                                }
                            }

                            @Override
                            public void onSubscribe(String channel, int subscribedChannels) {
                                log.info("Successfully subscribed to channel: {}", channel);
                            }

                            @Override
                            public void onUnsubscribe(String channel, int subscribedChannels) {
                                log.info("Unsubscribed from channel: {}", channel);
                            }
                        }, DSL_UPDATE_CHANNEL);
                    } catch (Exception e) {
                        if (running) {  // Only log if we're still supposed to be running
                            log.error("Redis subscription error, will retry in 1 second", e);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                log.info("Redis subscription loop terminated");
            });
        } catch (Exception e) {
            log.error("Failure starting Redis cache invalidation listener", e);
        }
    }

    private EntityVersionedCache<?> getCache(String dslId) {
        switch (dslId) {
            case NODE_EVENT_ID:
                return nodeDefinitionCache;
            case WORKFLOW_EVENT_ID:
                return workflowCache;
            default:
                log.warn("Unknown cache type: {}", dslId);
                return null;
        }
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping Redis cache invalidation listener");
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Redis listener didn't terminate in 5 seconds, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}