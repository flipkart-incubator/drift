package com.drift.worker.Utility;

import com.flipkart.kloud.config.ConfigClient;
import com.flipkart.kloud.config.error.ConfigServiceException;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigClientUtil {
    private static ConfigClient configClient;

    @Inject
    public ConfigClientUtil(ConfigClient configClient) {
        ConfigClientUtil.configClient = configClient;
    }

    public static String getString(String bucketName, String key) throws Exception {
        try {
            return configClient.getDynamicBucket(bucketName).getString(key);
        } catch (ConfigServiceException e) {
            log.error("Error fetching string from config bucket: {} with key: {}", bucketName, key, e);
            throw new Exception(e);
        }
    }
}
