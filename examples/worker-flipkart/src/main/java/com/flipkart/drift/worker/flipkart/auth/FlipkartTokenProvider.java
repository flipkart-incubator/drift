package com.flipkart.drift.worker.flipkart.auth;

import com.flipkart.drift.sdk.spi.auth.TokenProvider;
import com.flipkart.authn.AuthTokenService;
import com.netflix.config.DynamicProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Sample of TokenProvider implementaion. For reference only.
 */
@Slf4j
public class FlipkartTokenProvider implements TokenProvider {
    private AuthTokenService tokenService;

    public void FlipkartABTestingProvider() {
        // No-arg constructor required for ServiceLoader
    }

    @Override
    public void init() {
        try {
            // Fetch configuration from DynamicProperty
            String clientUrl = DynamicProperty.getInstance("auth.client.url").getString();
            String clientId = DynamicProperty.getInstance("auth.client.name").getString();
            String clientSecret = DynamicProperty.getInstance("auth.client.secret").getString();

            if (clientUrl == null || clientId == null || clientSecret == null) {
                log.error("Missing required authentication configuration: auth.client.url, auth.client.name, or auth.client.secret");
                throw new RuntimeException("Missing authentication configuration");
            }

            log.info("Initializing Flipkart AuthTokenService with clientUrl: {}, clientId: {}", clientUrl, clientId);
            
            AuthTokenService.init(clientUrl, clientId, clientSecret);
            tokenService = AuthTokenService.getInstance();
            
            log.info("Flipkart AuthTokenService initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize FlipkartTokenProvider", e);
            throw new RuntimeException("FlipkartTokenProvider initialization failed", e);
        }
    }

    @Override
    public String getAuthToken(String targetClientId) {
        if (tokenService == null) {
            log.error("FlipkartTokenProvider not initialized");
            return "";
        }
        try {
            String token = tokenService.fetchToken(targetClientId).toAuthorizationHeader();
            log.info("Token generated for client: {}", targetClientId);
            return token;
        } catch (Exception e) {
            log.error("Failed to generate token for client: {}", targetClientId, e);
            return "";
        }
    }

    @Override
    public boolean isInitialized() {
        return tokenService != null && tokenService.isInitialized();
    }
}

