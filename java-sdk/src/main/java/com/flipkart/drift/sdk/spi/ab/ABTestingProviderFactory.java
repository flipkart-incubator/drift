package com.flipkart.drift.sdk.spi.ab;

public class ABTestingProviderFactory {
    private static volatile ABTestingProvider provider = new NoOpABTestingProvider();

    public static synchronized void setProvider(ABTestingProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("ABTestingProvider cannot be null");
        }
        ABTestingProviderFactory.provider = provider;
    }

    public static ABTestingProvider getInstance() {
        return provider;
    }
}
