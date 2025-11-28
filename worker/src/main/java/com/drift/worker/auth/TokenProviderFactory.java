package com.drift.worker.auth;

import lombok.extern.slf4j.Slf4j;

import java.util.ServiceLoader;

/**
 * Factory for loading TokenProvider implementations.
 * Uses Java's ServiceLoader mechanism to discover implementations at runtime.
 */
@Slf4j
public class TokenProviderFactory {
    private static volatile TokenProvider instance;
    
    /**
     * Get the singleton TokenProvider instance.
     * The provider is loaded once and cached.
     * 
     * @return TokenProvider instance
     */
    public static TokenProvider getInstance() {
        if (instance == null) {
            synchronized (TokenProviderFactory.class) {
                if (instance == null) {
                    instance = loadTokenProvider();
                }
            }
        }
        return instance;
    }
    
    /**
     * Load TokenProvider using ServiceLoader.
     * Falls back to NoOpTokenProvider if no implementation is found.
     * 
     * @return TokenProvider instance
     */
    private static TokenProvider loadTokenProvider() {
        ServiceLoader<TokenProvider> loader = ServiceLoader.load(TokenProvider.class);
        
        for (TokenProvider provider : loader) {
            log.info("Loaded TokenProvider implementation: {}", provider.getClass().getName());
            return provider;
        }
        
        log.warn("No TokenProvider implementation found via SPI, using NoOpTokenProvider");
        return new NoOpTokenProvider();
    }
    
    /**
     * Set a specific TokenProvider instance.
     * Useful for testing or explicit configuration.
     * 
     * @param provider TokenProvider instance to use
     */
    public static void setInstance(TokenProvider provider) {
        synchronized (TokenProviderFactory.class) {
            instance = provider;
            log.info("TokenProvider explicitly set to: {}", provider.getClass().getName());
        }
    }

}

