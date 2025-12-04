package com.flipkart.drift.sdk.spi.auth;

import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.ServiceLoader;

@Slf4j
public class TokenProviderFactory {
    private static volatile TokenProvider provider;

    static {
        // Auto-discover TokenProvider implementations via ServiceLoader
        ServiceLoader<TokenProvider> loader = ServiceLoader.load(TokenProvider.class);
        Iterator<TokenProvider> iterator = loader.iterator();
        
        if (iterator.hasNext()) {
            provider = iterator.next();
            log.info("TokenProviderFactory: Discovered {} via SPI (not yet initialized)", provider.getClass().getName());
        } else {
            provider = new NoOpTokenProvider();
            log.info("TokenProviderFactory: No custom TokenProvider found via SPI, using NoOpTokenProvider");
        }
    }

    /**
     * Manually set a TokenProvider (optional, overrides SPI discovery).
     * This is useful for testing or dynamic provider switching.
     */
    public static synchronized void setProvider(TokenProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("TokenProvider cannot be null");
        }
        TokenProviderFactory.provider = provider;
        System.out.println("TokenProviderFactory: Manually set provider to " + provider.getClass().getName());
    }

    /**
     * Get the discovered or manually set TokenProvider instance.
     * Note: The provider may not be initialized yet. Call provider.init() if needed.
     */
    public static TokenProvider getInstance() {
        return provider;
    }
}
