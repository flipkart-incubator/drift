package com.flipkart.drift.commons.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public enum ObjectMapperUtil {
    INSTANCE;
    private final ObjectMapper objectMapper;

    ObjectMapperUtil() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public com.fasterxml.jackson.databind.ObjectMapper getMapper() {
        return objectMapper;
    }

    public <T> T getObj(String s, Class<T> clazz) throws IOException {
        return getMapper().readValue(s, clazz);
    }

    public <T> T getObj(JsonNode j, Class<T> clazz) throws IOException {
        return getMapper().treeToValue(j, clazz);
    }

    public JsonNode getJsonNode(Object obj) {
        return getMapper().convertValue(obj, JsonNode.class);
    }

    public ObjectNode getObjectNode(Object obj) {
        return getMapper().convertValue(obj, ObjectNode.class);
    }

    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Exception while writing value as string {}", e.getMessage());
            return null;
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.error("Exception while converting value {} to {}. Error: {}", json, typeRef, e.getMessage());
            return null;
        }
    }
}