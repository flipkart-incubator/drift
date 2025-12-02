package com.flipkart.drift.sdk.spi.auth;

public class TokenProviderFactory {
    private static TokenProvider provider = new NoOpTokenProvider();

    public static void setProvider(TokenProvider provider) {
        TokenProviderFactory.provider = provider;
    }

    public static TokenProvider getInstance() {
        return provider;
    }
}
