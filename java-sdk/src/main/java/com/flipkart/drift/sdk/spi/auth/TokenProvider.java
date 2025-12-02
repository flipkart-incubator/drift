package com.flipkart.drift.sdk.spi.auth;

/**
 * Interface for authentication token providers.
 * Implementations can provide different authentication mechanisms.
 * Each implementation is responsible for fetching its own configuration
 * using DynamicProperty or other configuration mechanisms.
 */
public interface TokenProvider {
    
    /**
     * Initialize the token provider.
     * Implementations should fetch their own configuration parameters
     * using DynamicProperty or similar configuration mechanisms.
     */
    void init();
    
    /**
     * Fetch authentication token for a target client.
     * 
     * @param targetClientId the client ID to fetch token for
     * @return Authorization header value (e.g., "Bearer xyz")
     */
    String getAuthToken(String targetClientId);
    
    /**
     * Check if the provider has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    boolean isInitialized();
}

