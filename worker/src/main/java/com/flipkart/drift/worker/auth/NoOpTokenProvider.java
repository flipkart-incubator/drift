package com.flipkart.drift.worker.auth;

import lombok.extern.slf4j.Slf4j;

/**
 * No-operation implementation of TokenProvider.
 * This is the default provider used when no authentication implementation is available.
 */
@Slf4j
public class NoOpTokenProvider implements TokenProvider {
    private boolean initialized = false;
    
    @Override
    public void init() {
        log.warn("NoOpTokenProvider initialized!");
        initialized = true;
    }
    
    @Override
    public String getAuthToken(String targetClientId) {
        log.debug("NoOpTokenProvider returning empty token for client: {}", targetClientId);
        return "";
    }
    
    @Override
    public boolean isInitialized() {
        return initialized;
    }
}

