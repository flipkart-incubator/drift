package com.flipkart.drift.api.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.commons.utils.MachineHelper;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.message.internal.ReaderWriter;
import org.slf4j.MDC;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Provider
@Slf4j
public class RequestFilter implements ContainerRequestFilter {

    public static final String TENANT_HEADER = "X_TENANT_ID";
    public static final String CLIENT_HEADER = "X_CLIENT_ID";
    public static final String X_PERF_HEADER = "X-PERF-TEST";
    public static final String X_PERF_HEADER_UNDERSCORE = "X_PERF_TEST";
    public static final String USERNAME_HEADER = "X_USERNAME_ID";
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS");
    private final ObjectMapper objectMapper;

    @Inject
    public RequestFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void filter(ContainerRequestContext containerRequestContext) {
        RequestThreadContext.get().clear();
        setTransactionId();
        javax.ws.rs.core.MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
        try {
            log.info("Headers : {}", objectMapper.writeValueAsString(headers));
        } catch (IOException e) {
            log.info("Headers: {}", headers);
        }
        logIncomingRequest(containerRequestContext, headers);
        extractHeaders(headers);
        setPerfRequestHeader(headers);
        log.info(RequestThreadContext.get().toString());
    }

    private void logIncomingRequest(ContainerRequestContext containerRequestContext, javax.ws.rs.core.MultivaluedMap<String, String> headers) {
        String url = containerRequestContext.getUriInfo().getAbsolutePath().toString();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = containerRequestContext.getEntityStream();

        try {
            String requestHeaders = this.objectMapper.writeValueAsString(headers);
            ReaderWriter.writeTo(in, out);
            byte[] requestEntity = out.toByteArray();
            containerRequestContext.setEntityStream(new ByteArrayInputStream(requestEntity));
            String body = out.toString();
            log.info("Request url: {} \n Body {} \n Headers {}", url, body, requestHeaders);
        } catch (IOException e) {
            log.error("Error in serializing {}", e.getMessage(), e);
            throw new ApiException(Response.Status.BAD_REQUEST, "Error in serializing {}", e.getMessage());
        }
    }

    private void extractHeaders(javax.ws.rs.core.MultivaluedMap<String, String> headers) {
        Optional<String> tenant = Optional.ofNullable(headers.getFirst(TENANT_HEADER));
        Optional<String> client = Optional.ofNullable(headers.getFirst(CLIENT_HEADER));
        Optional<String> username = Optional.ofNullable(headers.getFirst(USERNAME_HEADER));
        if (tenant.isEmpty() || client.isEmpty()) {
            throw new ApiException(Response.Status.BAD_REQUEST, "Headers are not valid , Headers required " + TENANT_HEADER + ", " + CLIENT_HEADER);
        }
        if (username.isEmpty()) {
            username = client;
        }
        RequestThreadContext.get().setTenant(tenant.get());
        RequestThreadContext.get().setClientId(client.get());
        RequestThreadContext.get().setUsername(username.get());
    }

    private void setTransactionId() {
        StringBuilder transactionId = new StringBuilder("DRIFT-");
        transactionId
                .append(LocalDateTime.now().format(dateTimeFormatter)).append("-")
                .append(String.valueOf(UUID.randomUUID().getLeastSignificantBits()), 1, 6)
                .append("-").append(MachineHelper.getMachineName());
        MDC.put("id", transactionId.toString());
        log.info("Transaction Id generated : {}", transactionId);
    }

    private void setPerfRequestHeader(javax.ws.rs.core.MultivaluedMap<String, String> headers) {
        boolean perfHeaderFlag = (headers.containsKey(X_PERF_HEADER) &&
                (headers.getFirst(X_PERF_HEADER).toLowerCase()).compareTo("true") == 0) ||
                (headers.containsKey(X_PERF_HEADER_UNDERSCORE) &&
                        (headers.getFirst(X_PERF_HEADER_UNDERSCORE).toLowerCase()).compareTo("true") == 0);
        RequestThreadContext.get().setPerfFlag(perfHeaderFlag);
    }
}

