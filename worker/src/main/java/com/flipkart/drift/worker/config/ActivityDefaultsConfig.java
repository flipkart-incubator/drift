package com.flipkart.drift.worker.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-level activity defaults bound from the {@code activityDefaults} YAML block.
 * <p>
 * Default values (maxAttempts=1, timeoutSeconds=10) match the values previously
 * hardcoded in {@code OptionsStore.activityOptionsV1}, so omitting this config block
 * is fully backward-compatible with existing deployments.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityDefaultsConfig {

    public ActivityDefaultsConfig(int defaultMaxAttempts, int defaultTimeoutSeconds) {
        this.defaultMaxAttempts = defaultMaxAttempts;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    @JsonProperty("defaultMaxAttempts")
    private int defaultMaxAttempts = 1;

    // 10s was the pre-existing hardcoded startToCloseTimeout — preserved here for backward compatibility
    @JsonProperty("defaultTimeoutSeconds")
    private int defaultTimeoutSeconds = 10;

    // WorkflowContextManagerActivity (HBase persist/resume/disconnected-node): short bound so a hung client call retries instead of blocking
    @JsonProperty("contextTimeoutSeconds")
    private int contextTimeoutSeconds = 15;

    @JsonProperty("contextMaxAttempts")
    private int contextMaxAttempts = 5;
}
