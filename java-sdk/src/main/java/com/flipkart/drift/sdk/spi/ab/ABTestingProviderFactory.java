package com.flipkart.drift.sdk.spi.ab;

public class ABTestingProviderFactory {
    private static ABTestingProvider provider = new NoOpABTestingProvider();

    public static void setProvider(ABTestingProvider provider) {
        ABTestingProviderFactory.provider = provider;
    }

    public static ABTestingProvider getInstance() {
        return provider;
    }
}
