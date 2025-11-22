package com.drift.worker.util;

import com.flipkart.kloud.authn.AuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum AuthNTokenGenerator {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(AuthNTokenGenerator.class);
    private AuthTokenService tokenService;

    public void init(String clientUrl, String clientId, String clientSecret) {
        AuthTokenService.init(clientUrl, clientId, clientSecret);
        initTokenService();
    }

    public void initTokenService() {
        tokenService = AuthTokenService.getInstance();
    }

    public String getAuthToken(String targetClientId) {
        String token = tokenService.fetchToken(targetClientId).toAuthorizationHeader();
        logger.info("Target Client Id {} | token generated {}", targetClientId, token);
        return token;
    }
}
