package com.flipkart.drift.persistence.bootstrap;

import lombok.Data;

/**
 * Top-level YAML mapping for yak.* settings. Expected YAML shape:
 * yak:
 *   namespace:
 *     hot: mart_drift_hot
 *     cold: ...
 */
@Data
public class YakConfig {
    private ConnectionNamespaceConfig namespace;
}