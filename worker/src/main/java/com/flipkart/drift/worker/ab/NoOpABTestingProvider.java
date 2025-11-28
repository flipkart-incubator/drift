package com.flipkart.drift.worker.ab;

import lombok.extern.slf4j.Slf4j;

/**
 * No-operation implementation of ABTestingProvider.
 * Always returns false (control bucket) for all tests.
 * This is the default provider used when no A/B testing implementation is available.
 */
@Slf4j
public class NoOpABTestingProvider implements ABTestingProvider {
    private boolean initialized = false;
    
    @Override
    public void init() {
        log.warn("NoOpABTestingProvider initialized - all traffic will go to control bucket");
        initialized = true;
    }
    
    @Override
    public boolean isInTreatment(String pivotValue, String experimentName, String variable) {
        log.debug("NoOpABTestingProvider: returning false (control) for pivot: {}, experiment: {}, variable: {}", 
                  pivotValue, experimentName, variable);
        return false; // Always return control
    }
    
    @Override
    public boolean isInitialized() {
        return initialized;
    }
}

