package com.flipkart.drift.persistence.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Map;

@Provider
@Slf4j
public class CustomJsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {
    private final ObjectMapper objectMapper;

    @Inject
    public CustomJsonMappingExceptionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Response toResponse(JsonMappingException exception) {
        log.info(" There was a exception in the system");
        log.error("ERROR : " + exception.getMessage());
        Map<String, Object> map = Maps.newHashMap();
        Response.Status status = Response.Status.BAD_REQUEST;
        map.put("transactionId", MDC.get("id"));
        map.put("status", status.toString());
        map.put("message", exception.getMessage());
        try {
            return Response.status(status).entity(objectMapper.writeValueAsString(map)).type(MediaType.APPLICATION_JSON_TYPE).build();
        } catch (IOException e) {
            return Response.status(status).build();
        }
    }
}

