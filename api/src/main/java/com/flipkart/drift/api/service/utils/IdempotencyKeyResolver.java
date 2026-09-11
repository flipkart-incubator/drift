package com.flipkart.drift.api.service.utils;

import com.flipkart.drift.api.config.IdempotencyConfig;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.filters.IdempotencyKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves the business-key idempotency header on {@code POST /v3/workflow/start}
 * into a raw key and, from there, a deterministic Temporal workflowId, isolated by
 * tenant and clientId via a SHA-256-derived workflowId.
 */
@Slf4j
@Singleton
public class IdempotencyKeyResolver {

    private static final String WORKFLOW_ID_PREFIX = "WF-";
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_\\-:.]{1,256}");

    public static final String ERROR_MISSING = "IDEMPOTENCY_KEY_MISSING";
    public static final String ERROR_AMBIGUOUS = "IDEMPOTENCY_KEY_AMBIGUOUS";
    public static final String ERROR_MALFORMED = "IDEMPOTENCY_KEY_MALFORMED";

    private final IdempotencyConfig idempotencyConfig;

    @Inject
    public IdempotencyKeyResolver(IdempotencyConfig idempotencyConfig) {
        this.idempotencyConfig = idempotencyConfig;
    }

    /**
     * Extracts and validates the raw idempotency key from the configured header(s).
     *
     * @return {@code Optional.empty()} only when no configured header is present and
     * {@code idempotencyConfig.isOptional()} is true (legacy fall-through path).
     */
    public Optional<String> extractRawKey(MultivaluedMap<String, String> headers) {
        Set<String> distinctValues = new LinkedHashSet<>();
        for (String headerName : idempotencyConfig.getHeaders()) {
            String value = headers.getFirst(headerName);
            if (value != null && !value.isBlank()) {
                distinctValues.add(value);
            }
        }

        if (distinctValues.isEmpty()) {
            if (idempotencyConfig.isOptional()) {
                return Optional.empty();
            }
            throw new ApiException(Response.Status.BAD_REQUEST,
                    "Idempotency key header is required but was not supplied", ERROR_MISSING);
        }

        if (distinctValues.size() > 1) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    "Multiple idempotency key headers supplied with conflicting values", ERROR_AMBIGUOUS);
        }

        String rawKey = distinctValues.iterator().next();
        if (!VALID_KEY_PATTERN.matcher(rawKey).matches()) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    "Idempotency key does not match required format [A-Za-z0-9_\\-:.]{1,256}", ERROR_MALFORMED);
        }

        return Optional.of(rawKey);
    }

    /**
     * Derives a deterministic Temporal workflowId from tenant + clientId + rawKey.
     * All three components are bound into the SHA-256 input using a NUL-byte separator
     * (U+0000 cannot appear in HTTP header values or the allowed rawKey charset), so the
     * hash is unique per (tenant, clientId, rawKey) triple even when the lowercased
     * "{tenant}-{clientId}" prefix looks identical across different splits
     * (e.g. tenant="a", clientId="b-c" vs tenant="a-b", clientId="c").
     */
    public String toWorkflowId(String tenant, String clientId, String rawKey) {
        String t = tenant.toLowerCase();
        String c = clientId.toLowerCase();
        String hashInput = t + "\0" + c + "\0" + rawKey;
        return WORKFLOW_ID_PREFIX + t + "-" + c + "-" + DigestUtils.sha256Hex(hashInput);
    }

    public IdempotencyKey resolve(String tenant, String clientId, String rawKey) {
        String workflowId = idempotencyConfig.isUseRawKeyAsWorkflowId()
                ? rawKey
                : toWorkflowId(tenant, clientId, rawKey);
        return IdempotencyKey.builder()
                .tenant(tenant)
                .clientId(clientId)
                .rawKey(rawKey)
                .workflowId(workflowId)
                .build();
    }
}
