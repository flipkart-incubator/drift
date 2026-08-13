package com.flipkart.drift.api.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for DNS-fanout cache invalidation to worker pods.
 * Used when Redis pub-sub is unavailable (redisEnabled=false).
 * Set headlessServiceHost to a K8s headless service DNS name;
 * InetAddress.getAllByName() will resolve it to all pod IPs.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkerInvalidationConfig {
    /** K8s headless service DNS name for workers (resolves to all pod IPs). */
    private String headlessServiceHost;
    /** Dropwizard admin port on worker pods (matches adminConnectors port in worker config). */
    private int adminPort = 5601;
    /** Per-pod HTTP connect+read timeout in milliseconds. */
    private int perPodTimeoutMs = 2000;
    /** Set to false to suppress fanout even when Redis is disabled. */
    private boolean enabled = true;
}
