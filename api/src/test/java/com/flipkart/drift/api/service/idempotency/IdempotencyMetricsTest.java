package com.flipkart.drift.api.service.idempotency;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyMetricsTest {

    @Test
    void missMarksPerTenantClientMeter() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.miss("Tenant-1", "Client-1");

        assertEquals(1, registry.meter(IdempotencyMetrics.MISS + ".tenant-1.client-1").getCount());
    }

    @Test
    void alreadyStartedMarksPerTenantClientMeter() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.alreadyStarted("Tenant-1", "Client-1");

        assertEquals(1, registry.meter(IdempotencyMetrics.ALREADY_STARTED + ".tenant-1.client-1").getCount());
    }

    @Test
    void historyPurgedMarksPerTenantClientMeter() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.historyPurged("Tenant-1", "Client-1");

        assertEquals(1, registry.meter(IdempotencyMetrics.HISTORY_PURGED + ".tenant-1.client-1").getCount());
    }

    @Test
    void differentTenantsProduceSeparateMeters() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.miss("tenantA", "web");
        metrics.miss("tenantA", "web");
        metrics.miss("tenantB", "web");

        assertEquals(2, registry.meter(IdempotencyMetrics.MISS + ".tenanta.web").getCount());
        assertEquals(1, registry.meter(IdempotencyMetrics.MISS + ".tenantb.web").getCount());
    }

    @Test
    void differentClientsProduceSeparateMeters() {
        MetricRegistry registry = new MetricRegistry();
        IdempotencyMetrics metrics = new IdempotencyMetrics(registry);

        metrics.miss("tenant1", "web");
        metrics.miss("tenant1", "mobile");

        assertEquals(1, registry.meter(IdempotencyMetrics.MISS + ".tenant1.web").getCount());
        assertEquals(1, registry.meter(IdempotencyMetrics.MISS + ".tenant1.mobile").getCount());
    }
}
