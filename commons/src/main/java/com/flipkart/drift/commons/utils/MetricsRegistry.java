package com.flipkart.drift.commons.utils;

import com.codahale.metrics.*;
import com.codahale.metrics.jmx.JmxReporter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public enum MetricsRegistry {
    INSTANCE;
    private static final String SEPARATOR = ".";
    private final MetricRegistry metricRegistry;

    MetricsRegistry() {
        metricRegistry = new MetricRegistry();
        JmxReporter jmxReporter = JmxReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        jmxReporter.start();
    }

    public static <T extends Metric> void registerGauge(Class<?> callerClass, T metric, String... names) {
        String metricName = MetricRegistry.name(callerClass, names);
        try {
            INSTANCE.getRegistry().register(metricName, metric);
        } catch (IllegalArgumentException e) {
            // Metric already exists, return existing one
            log.warn("Metric {} already registered, skipping registration", metricName);
            @SuppressWarnings("unchecked")
            T existingMetric = (T) INSTANCE.getRegistry().getMetrics().get(metricName);
        }
    }

    public static Gauge getGauge(Class<?> callerClass, String key) {
        String metricName = MetricRegistry.name(callerClass, key);
        return INSTANCE.getRegistry().getGauges().get(metricName);
    }

    public static void incrementCounter(String name) {
        INSTANCE.getRegistry().counter(name).inc();
    }


    public static Timer.Context timerContext(String metricName) {
        return INSTANCE.getRegistry().timer(MetricRegistry.name(metricName)).time();
    }

    public static Timer.Context timerContext(Class<?> callerClass, String... names) {
        return INSTANCE.getRegistry().timer(MetricRegistry.name(callerClass, names)).time();
    }

    public static void histogram(Class<?> callerClass, long number, String... names) {
        INSTANCE.getRegistry().histogram(MetricRegistry.name(callerClass, names)).update(number);
    }

    public static void histogram(long number, String... names) {
        String name = join(names);
        INSTANCE.getRegistry().histogram(MetricRegistry.name(name)).update(number);
    }

    public static void markMeter(String... names) {
        String name = join(names);
        INSTANCE.getRegistry().meter(MetricRegistry.name(name)).mark();
    }

    public static void markMeter(Class<?> callerClass, long count, String... names) {
        INSTANCE.getRegistry().meter(MetricRegistry.name(callerClass, names)).mark(count);
    }

    public static void markMeter(Class<?> callerClass, String... names) {
        markMeter(callerClass, 1, names);
    }

    private static String join(String... values) {
        return String.join(SEPARATOR, values);
    }

    public MetricRegistry getRegistry() {
        return metricRegistry;
    }

    public static MetricRegistry getMetricRegistry() {
        return INSTANCE.getRegistry();
    }
}