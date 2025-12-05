package com.flipkart.drift.worker.flipkart.ab;

import com.flipkart.abservice.models.request.ABExperimentRequest;
import com.flipkart.drift.sdk.spi.ab.ABTestingProvider;
import com.flipkart.abservice.models.response.ABVariableResponse;
import com.flipkart.abservice.pojo.UserID;
import com.flipkart.abservice.resources.ABService;
import com.flipkart.abservice.store.impl.ABApiConfigStore;
import com.netflix.config.DynamicProperty;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Flipkart-specific implementation of ABTestingProvider.
 *
 * NOTE: This implementation depends on internal Flipkart libraries.
 * This class is in a separate module (worker-flipkart) that can be excluded
 * from public distributions.
 */
@Slf4j
public class FlipkartABTestingProvider implements ABTestingProvider {
    private String abClientId;
    private boolean initialized = false;

    /**
     * No-arg constructor for SPI discovery.
     */
    public FlipkartABTestingProvider() {
        // No-arg constructor required for ServiceLoader
    }

    @Override
    public void init() {
        if (initialized) {
            log.debug("FlipkartABTestingProvider already initialized");
            return;
        }

        try {
            // Fetch ab.client.id from DynamicProperty
            this.abClientId = DynamicProperty.getInstance("ab.client.id").getString();
            String tenantId = DynamicProperty.getInstance("ab.tenant.id").getString();
            String endpoint = DynamicProperty.getInstance("ab.endpoint").getString();
            String secret = DynamicProperty.getInstance("ab.client.secret.key").getString();
            
            log.info("Initializing Flipkart ABService with tenant: {}, endpoint: {}", tenantId, endpoint);
            
            ABApiConfigStore configStore = ABApiConfigStore.initialize(tenantId, endpoint, secret);
            ABService.initialize(configStore);

            if (abClientId == null || abClientId.trim().isEmpty()) {
                log.warn("ab.client.id not configured, A/B testing may not work correctly");
            }
            
            ABService instance = ABService.getInstance();
            if (instance != null) {
                initialized = true;
                log.info("Flipkart ABService initialized successfully");
            } else {
                throw new RuntimeException("ABService initialization failed - getInstance returned null");
            }

        } catch (Exception e) {
            log.error("Failed to initialize FlipkartABTestingProvider", e);
            throw new RuntimeException("FlipkartABTestingProvider initialization failed", e);
        }
    }

    @Override
    public boolean isInTreatment(String pivotValue, String experimentName, String variable) {
        if (!initialized) {
            log.warn("FlipkartABTestingProvider not initialized, returning control");
            return false;
        }

        try {
            boolean result = isCustomerInTreatment(pivotValue, experimentName, variable);
            log.debug("AB Test - pivot: {}, experiment: {}, variable: {}, result: {}",
                    pivotValue, experimentName, variable, result ? "TREATMENT" : "CONTROL");
            return result;
        } catch (Exception e) {
            log.error("Error in A/B testing for pivot: {}, experiment: {}, variable: {}",
                    pivotValue, experimentName, variable, e);
            return false; // Default to control on error
        }
    }

    /**
     * Check if a customer is in the treatment group for an experiment.
     * Includes metrics tracking and error handling.
     */
    private boolean isCustomerInTreatment(String userId, String experimentName, String variableName) {
        ABVariableResponse abResponse = getABV2Response(userId, experimentName);
        if (abResponse != null && abResponse.getAbId() != null) {
            Map<String, Object> abVariable = abResponse.getAllVariables();
            Boolean variableValue = (Boolean) abVariable.get(variableName);
            return Boolean.TRUE.equals(variableValue);
        }
        return false;
    }

    /**
     * Get AB response from Flipkart ABService with metrics tracking.
     */
    private ABVariableResponse getABV2Response(String userId, String experimentName) {
        try {
            UserID userID = new UserID(userId, null);
            ABExperimentRequest abRequest = new ABExperimentRequest(userID, experimentName);
            abRequest.setClientID(abClientId);
            return ABService.getInstance().getABVariables(abRequest);
        } catch (Exception e) {
            log.error("Error calling ABService for user {} experiment {}: {}",
                    userId, experimentName, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}

