package com.flipkart.drift.worker.Utility;


import com.flipkart.drift.sdk.spi.scheduler.SchedulerProvider;
import com.flipkart.drift.sdk.spi.scheduler.SchedulerProviderFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SchedulerInitializer {

    private final SchedulerProvider schedulerProvider;

    @Getter
    private volatile boolean initialized = false;

    @Inject
    public SchedulerInitializer() {
        log.info("Initializing Scheduler system");
        this.schedulerProvider = SchedulerProviderFactory.getInstance();
        initializeSchedulerProvider();
    }

    private void initializeSchedulerProvider() {
        if (initialized) {
            log.debug("Scheduler provider already initialized, skipping...");
            return;
        }

        try {
            log.info("Initializing Scheduler provider: {}", schedulerProvider.getClass().getSimpleName());

            schedulerProvider.init();

            if (schedulerProvider.isInitialized()) {
                initialized = true;
                log.info("scheduler provider initialized successfully: {}",
                        schedulerProvider.getClass().getSimpleName());
            } else {
                throw new RuntimeException("scheduler provider initialization failed");
            }

        } catch (Exception e) {
            log.error("Failed to initialize scheduler provider: {}", e.getMessage(), e);
            throw new RuntimeException("scheduler provider initialization failed", e);
        }
    }

    /**
     * Get the initialized scheduler provider.
     */
    public SchedulerProvider getProvider() {
        return schedulerProvider;
    }
}
