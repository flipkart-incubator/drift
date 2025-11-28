package com.drift.worker.ab;

/**
 * Interface for A/B testing providers.
 * Implementations can provide different A/B testing mechanisms
 * while keeping the core application code independent of specific A/B libraries.
 */
public interface ABTestingProvider {
    
    /**
     * Initialize the A/B testing provider.
     * Implementations should fetch their own configuration if needed.
     */
    void init();
    
    /**
     * Determine if a given pivot value falls into the treatment bucket for an experiment.
     * 
     * @param pivotValue The value to test (e.g., customerId, orderId)
     * @param experimentName The name of the experiment
     * @param variable The variable name within the experiment
     * @return true if in treatment bucket, false if in control bucket
     */
    boolean isInTreatment(String pivotValue, String experimentName, String variable);
    
    /**
     * Check if the provider has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    boolean isInitialized();
}

