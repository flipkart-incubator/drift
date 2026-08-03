package com.flipkart.drift.api.service.idempotency;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Locale;

/**
 * Codahale meters for the business-key idempotency feature: {@code miss} (a fresh workflow
 * start), {@code already_started} (Temporal detected a duplicate start), and
 * {@code history_purged} (the duplicate's history was no longer queryable, so a retry
 * was attempted).
 *
 * <p>Metric names are structured as {@code <base>.<tenant>.<clientId>} so Grafana can slice
 * by tenant ({@code drift.idempotency.miss.<tenant>.*}) or by client
 * ({@code drift.idempotency.miss.*.<clientId>}) using wildcard queries.
 */
@Singleton
public class IdempotencyMetrics {

    static final String MISS              = "drift.idempotency.miss";
    static final String ALREADY_STARTED   = "drift.idempotency.temporal.already_started";
    static final String HISTORY_PURGED    = "drift.idempotency.temporal.history_purged";

    private final MetricRegistry metricRegistry;

    @Inject
    public IdempotencyMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public void miss(String tenant, String clientId) {
        meter(MISS, tenant, clientId).mark();
    }

    public void alreadyStarted(String tenant, String clientId) {
        meter(ALREADY_STARTED, tenant, clientId).mark();
    }

    public void historyPurged(String tenant, String clientId) {
        meter(HISTORY_PURGED, tenant, clientId).mark();
    }

    private Meter meter(String base, String tenant, String clientId) {
        return metricRegistry.meter(base + "." + tenant.toLowerCase(Locale.ROOT) + "." + clientId.toLowerCase(Locale.ROOT));
    }
}
