package com.flipkart.drift.api.client;

import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.config.WorkerInvalidationConfig;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisSentinelPool;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.flipkart.drift.api.service.utils.Utility.publishRedisEvent;
import static com.flipkart.drift.commons.utils.Constants.Workflow.DSL_UPDATE_CHANNEL;

/**
 * Publishes DSL cache invalidation events to all worker pods.
 * Strategy:
 *   - Redis enabled and pool available → publish to DSL_UPDATE_CHANNEL (existing behaviour).
 *   - Redis disabled → DNS fanout: resolve K8s headless service → POST to each worker pod's admin task endpoint.
 */
@Slf4j
public class CacheInvalidationClient {

    private final boolean redisEnabled;
    private final JedisSentinelPool jedisSentinelPool;
    private final WorkerInvalidationConfig workerInvalidationConfig;

    public CacheInvalidationClient(DriftConfiguration driftConfiguration, JedisSentinelPool jedisSentinelPool) {
        this.redisEnabled = driftConfiguration.getRedisConfiguration().isRedisEnabled();
        this.jedisSentinelPool = jedisSentinelPool;
        this.workerInvalidationConfig = driftConfiguration.getWorkerInvalidationConfig();
    }

    /**
     * Invalidates the cache entry for the given key on all worker pods.
     *
     * @param cacheType  "NODE" or "WORKFLOW"
     * @param key        HBase row key to invalidate, or "ALL"
     */
    public void invalidate(String cacheType, String key) {
        if (redisEnabled && jedisSentinelPool != null) {
            publishRedisEvent(jedisSentinelPool, DSL_UPDATE_CHANNEL, cacheType + " " + key);
        } else {
            log.info("Redis unavailable; using DNS fanout for cache invalidation: type={}, key={}", cacheType, key);
            fanoutToWorkers(cacheType, key);
        }
    }

    private void fanoutToWorkers(String cacheType, String key) {
        if (workerInvalidationConfig == null || !workerInvalidationConfig.isEnabled()
                || workerInvalidationConfig.getHeadlessServiceHost() == null
                || workerInvalidationConfig.getHeadlessServiceHost().isBlank()) {
            log.debug("Worker invalidation config not set or disabled, skipping fanout");
            return;
        }
        fanout(workerInvalidationConfig.getHeadlessServiceHost(),
                workerInvalidationConfig.getAdminPort(),
                workerInvalidationConfig.getPerPodTimeoutMs(),
                cacheType, key);
    }

    private void fanout(String headlessServiceHost, int adminPort, int timeoutMs,
                        String cacheType, String key) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(headlessServiceHost);
            log.info("DNS fanout to {} worker pod(s) for cache type={} key={}", addresses.length, cacheType, key);
            for (InetAddress address : addresses) {
                postInvalidation(address.getHostAddress(), adminPort, timeoutMs, cacheType, key);
            }
        } catch (Exception e) {
            log.error("Error during DNS fanout to workers for cache type={} key={}", cacheType, key, e);
        }
    }

    private void postInvalidation(String ip, int port, int timeoutMs, String cacheType, String key) {
        String urlStr = String.format("http://%s:%d/tasks/cache-invalidate?type=%s&key=%s", ip, port,
                URLEncoder.encode(cacheType, StandardCharsets.UTF_8),
                URLEncoder.encode(key, StandardCharsets.UTF_8));
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            int responseCode = conn.getResponseCode();
            log.debug("Cache invalidation POST to {} returned {}", urlStr, responseCode);
        } catch (Exception e) {
            log.error("Failed to POST cache invalidation to {}: {}", urlStr, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
