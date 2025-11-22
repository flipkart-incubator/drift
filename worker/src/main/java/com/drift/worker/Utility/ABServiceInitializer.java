package com.drift.worker.Utility;

import com.flipkart.abservice.store.impl.ABApiConfigStore;
import com.flipkart.abservice.resources.ABService;
import com.drift.worker.config.ABConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ABServiceInitializer {
    
    private final ABConfiguration abConfiguration;
    @Getter
    private volatile boolean initialized = false;
    
    @Inject
    public ABServiceInitializer(ABConfiguration abConfiguration) {
        this.abConfiguration = abConfiguration;
        initializeABService();
    }
    
    private void initializeABService() {
        if (initialized) {
            log.debug("ABService already initialized, skipping...");
            return;
        }
        try {
            log.info("Initializing ABService with tenant: {}, endpoint: {}", 
                    abConfiguration.getTenantId(), abConfiguration.getEndpoint());
            
            ABApiConfigStore configStore = ABApiConfigStore.initialize(
                    abConfiguration.getTenantId(),
                    abConfiguration.getEndpoint(),
                    abConfiguration.getClientSecretKey()
            );
            
            ABService.initialize(configStore);

            ABService instance = ABService.getInstance();
            if (instance != null) {
                initialized = true;
                log.info("ABService initialized successfully");
            } else {
                throw new RuntimeException("ABService initialization failed - getInstance returned null");
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize ABService: {}", e.getMessage(), e);
            throw new RuntimeException("ABService initialization failed", e);
        }
    }

} 