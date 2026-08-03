package com.flipkart.drift.api.service.idempotency;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Codahale meters for the business-key idempotency feature: {@code miss} (a fresh workflow
 * start), {@code already_started} (Temporal detected a duplicate start), and
 * {@code history_purged} (the duplicate's history was no longer queryable, so a retry
 * was attempted).
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
