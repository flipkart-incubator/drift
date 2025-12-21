package com.flipkart.drift.sdk.spi.scheduler;

import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.ServiceLoader;

@Slf4j
public class SchedulerProviderFactory {
    private static volatile SchedulerProvider provider;

    static {
        // Auto-discover SchedulerProvider implementations via ServiceLoader
        ServiceLoader<SchedulerProvider> loader = ServiceLoader.load(SchedulerProvider.class);
        Iterator<SchedulerProvider> iterator = loader.iterator();

        if (iterator.hasNext()) {
            provider = iterator.next();
            log.info("SchedulerProviderFactory: Discovered {} via SPI (not yet initialized)", provider.getClass().getName());
        } else {
            provider = new NoOpSchedulerProvider();
            log.info("SchedulerProviderFactory: No custom SchedulerProvider found via SPI, using NoOpSchedulerProvider");
        }
    }

    /**
     * Manually set an SchedulerProvider (optional, overrides SPI discovery).
     * This is useful for testing or dynamic provider switching.
     */
    public static synchronized void setProvider(SchedulerProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("SchedulerProvider cannot be null");
        }
        SchedulerProviderFactory.provider = provider;
        log.info("SchedulerProviderFactory: Manually set provider to " + provider.getClass().getName());
    }

    /**
     * Get the discovered or manually set SchedulerProvider instance.
     * Note: The provider may not be initialized yet. Call provider.init() if needed.
     */
    public static SchedulerProvider getInstance() {
        return provider;
    }

}
