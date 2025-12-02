package com.flipkart.drift.sdk.spi.auth;

public class TokenProviderFactory {
    private static volatile TokenProvider provider = new NoOpTokenProvider();

    public static synchronized void setProvider(TokenProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("TokenProvider cannot be null");
        }
        TokenProviderFactory.provider = provider;
    }

    public static TokenProvider getInstance() {
        return provider;
    }
}
