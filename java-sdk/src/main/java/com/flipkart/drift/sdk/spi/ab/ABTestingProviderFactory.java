package com.flipkart.drift.sdk.spi.ab;

import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.ServiceLoader;

@Slf4j
public class ABTestingProviderFactory {
    private static volatile ABTestingProvider provider;

    static {
        // Auto-discover ABTestingProvider implementations via ServiceLoader
        ServiceLoader<ABTestingProvider> loader = ServiceLoader.load(ABTestingProvider.class);
        Iterator<ABTestingProvider> iterator = loader.iterator();
        
        if (iterator.hasNext()) {
            provider = iterator.next();
            log.info("ABTestingProviderFactory: Discovered {} via SPI (not yet initialized)", provider.getClass().getName());
        } else {
            provider = new NoOpABTestingProvider();
            log.info("ABTestingProviderFactory: No custom ABTestingProvider found via SPI, using NoOpABTestingProvider");
        }
    }

    /**
     * Manually set an ABTestingProvider (optional, overrides SPI discovery).
     * This is useful for testing or dynamic provider switching.
     */
    public static synchronized void setProvider(ABTestingProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("ABTestingProvider cannot be null");
        }
        ABTestingProviderFactory.provider = provider;
        log.info("ABTestingProviderFactory: Manually set provider to " + provider.getClass().getName());
    }

    /**
     * Get the discovered or manually set ABTestingProvider instance.
     * Note: The provider may not be initialized yet. Call provider.init() if needed.
     */
    public static ABTestingProvider getInstance() {
        return provider;
    }
}
