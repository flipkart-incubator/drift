package com.flipkart.drift.api.service.idempotency;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Codahale meters for the business-key idempotency feature (§3.4, Redis-free variant).
 * <p>
 * Only the §3.4-applicable rows from LLD §11.1 are implemented here: {@code miss},
 * {@code already_started}, {@code history_purged}. The §3.2 (Redis-backed)-only meters
 * ({@code hit}, {@code conflict.in_flight}, {@code redis.error}, {@code latency.lookup},
 * {@code latency.write}) are intentionally NOT implemented — there is no cache under §3.4.
 */
@Singleton
public class IdempotencyMetrics {
    private final MetricRegistry metricRegistry;

    @Inject
    public IdempotencyMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public void miss(String tenant, String clientId) {
        meter("drift.idempotency.miss").mark();
    }

    public void alreadyStarted(String tenant) {
        meter("drift.idempotency.temporal.already_started").mark();
    }

    public void historyPurged(String tenant) {
        meter("drift.idempotency.temporal.history_purged").mark();
    }

    private Meter meter(String name) {
        return metricRegistry.meter(MetricRegistry.name(name));
    }
}
