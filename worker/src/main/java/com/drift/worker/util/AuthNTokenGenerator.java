package com.drift.worker.util;

import com.drift.worker.auth.TokenProvider;
import com.drift.worker.auth.TokenProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton wrapper for authentication token generation.
 * Delegates to pluggable TokenProvider implementation loaded via SPI.
 */
public enum AuthNTokenGenerator {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(AuthNTokenGenerator.class);
    private TokenProvider tokenProvider;

    /**
     * Initialize the token generator.
     * The TokenProvider implementation will fetch its own configuration
     * using DynamicProperty.
     */
    public void init() {
        if (tokenProvider == null) {
            tokenProvider = TokenProviderFactory.getInstance();
        }
        tokenProvider.init();
        logger.info("AuthNTokenGenerator initialized with provider: {}", 
                   tokenProvider.getClass().getSimpleName());
    }

    /**
     * Get authentication token for a target client.
     * 
     * @param targetClientId the client ID to fetch token for
     * @return Authorization header value
     */
    public String getAuthToken(String targetClientId) {
        if (tokenProvider == null) {
            logger.warn("TokenProvider not initialized, returning empty token");
            return "";
        }
        return tokenProvider.getAuthToken(targetClientId);
    }
    
    /**
     * Check if the token generator has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return tokenProvider != null && tokenProvider.isInitialized();
    }
}
