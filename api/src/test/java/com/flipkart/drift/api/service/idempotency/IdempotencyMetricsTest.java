package com.flipkart.drift.api.service.idempotency;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyMetricsTest {

    @Test
    void missMarksCorrectlyNamedMeter() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.miss("tenant-1", "client-1");

        assertEquals(1, registry.meter("drift.idempotency.miss").getCount());
    }

    @Test
    void alreadyStartedMarksCorrectlyNamedMeter() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.alreadyStarted("tenant-1");

        assertEquals(1, registry.meter("drift.idempotency.temporal.already_started").getCount());
    }

    @Test
    void historyPurgedMarksCorrectlyNamedMeter() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.historyPurged("tenant-1");

        assertEquals(1, registry.meter("drift.idempotency.temporal.history_purged").getCount());
    }
}
