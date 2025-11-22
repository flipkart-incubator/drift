package com.drift.worker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.drift.commons.exception.ApiException;
import com.drift.commons.utils.ObjectMapperUtil;
import com.flipkart.kloud.config.ConfigClient;
import com.flipkart.kloud.config.DynamicBucket;
import com.flipkart.kloud.config.error.ConfigServiceException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.util.*;

@Getter
@Slf4j
public class WorkflowConfigStoreService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE_DEF = new TypeReference<Map<String, Object>>() {
    };
    private static final String ENUM_MAP_KEY = "enum_map";
    private static final String TEMPLATE_KEY = "template_list";
    private final ConfigClient configClient;
    private final String bucketName;

    @Inject
    public WorkflowConfigStoreService(ConfigClient configClient, @Named("enumStoreBucket") String bucketName) {
        this.configClient = configClient;
        this.bucketName = bucketName;
    }

    public Map<String, Object> getEnumMapping() {
        try {
            DynamicBucket bucket = getBucket();
            return extractEnumMapping(bucket);
        } catch (ConfigServiceException e) {
            log.error("Failed to fetch enum mapping from bucket: {}", bucketName, e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to fetch enum mapping");
        }
    }

    private DynamicBucket getBucket() throws ConfigServiceException {
        return configClient.getDynamicBucket(bucketName);
    }

    private Map<String, Object> extractEnumMapping(DynamicBucket bucket) {
        if (!bucket.getKeys().containsKey(ENUM_MAP_KEY)) {
            log.warn("Enum map key not found in bucket: {}", bucketName);
            return new HashMap<>();
        }

        List<String> values = bucket.getStringArray(ENUM_MAP_KEY);
        if (values.isEmpty()) {
            log.warn("Empty enum map values found in bucket: {}", bucketName);
            return new HashMap<>();
        }
        try {
            String mapJson = ObjectMapperUtil.INSTANCE.toJson(values.get(0));
            return ObjectMapperUtil.INSTANCE.fromJson(mapJson, MAP_TYPE_DEF);
        } catch (Exception e) {
            log.error("Failed to parse enum mapping from bucket: {}", bucketName, e);
            return new HashMap<>();
        }
    }
}