package com.flipkart.drift.worker.ab;

import lombok.extern.slf4j.Slf4j;

import java.util.ServiceLoader;

/**
 * Factory for loading ABTestingProvider implementations.
 * Uses Java's ServiceLoader mechanism to discover implementations at runtime.
 */
@Slf4j
public class ABTestingProviderFactory {
    private static volatile ABTestingProvider instance;
    
    /**
     * Get the singleton ABTestingProvider instance.
     * The provider is loaded once and cached.
     * 
     * @return ABTestingProvider instance
     */
    public static ABTestingProvider getInstance() {
        if (instance == null) {
            synchronized (ABTestingProviderFactory.class) {
                if (instance == null) {
                    instance = loadABTestingProvider();
                }
            }
        }
        return instance;
    }
    
    /**
     * Load ABTestingProvider using ServiceLoader.
     * Falls back to NoOpABTestingProvider if no implementation is found.
     * 
     * @return ABTestingProvider instance
     */
    private static ABTestingProvider loadABTestingProvider() {
        ServiceLoader<ABTestingProvider> loader = ServiceLoader.load(ABTestingProvider.class);
        
        for (ABTestingProvider provider : loader) {
            log.info("Loaded ABTestingProvider implementation: {}", provider.getClass().getName());
            return provider;
        }
        
        log.warn("No ABTestingProvider implementation found via SPI, using NoOpABTestingProvider");
        log.warn("All A/B tests will return control bucket");
        return new NoOpABTestingProvider();
    }
    
    /**
     * Set a specific ABTestingProvider instance.
     * Useful for testing or explicit configuration.
     * 
     * @param provider ABTestingProvider instance to use
     */
    public static void setInstance(ABTestingProvider provider) {
        synchronized (ABTestingProviderFactory.class) {
            instance = provider;
            log.info("ABTestingProvider explicitly set to: {}", provider.getClass().getName());
        }
    }
}

