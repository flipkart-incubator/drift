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
 * into a raw key and, from there, a deterministic Temporal workflowId.
 * <p>
 * §3.4 (Redis-free) scope: no cache-key derivation, only tenant+clientId+rawKey isolation
 * via a SHA-256-derived workflowId (LLD §3.3, §6.3).
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
     * Derives a deterministic Temporal workflowId from tenant + clientId + rawKey (LLD §3.3/§6.3).
     * Two different clientIds under the same tenant, or two different tenants, always produce
     * distinct workflowIds even for an identical rawKey.
     */
    public String toWorkflowId(String tenant, String clientId, String rawKey) {
        return WORKFLOW_ID_PREFIX + tenant.toLowerCase() + "-" + clientId.toLowerCase()
                + "-" + DigestUtils.sha256Hex(rawKey);
    }

    public IdempotencyKey resolve(String tenant, String clientId, String rawKey) {
        return IdempotencyKey.builder()
                .tenant(tenant)
                .clientId(clientId)
                .rawKey(rawKey)
                .workflowId(toWorkflowId(tenant, clientId, rawKey))
                .build();
    }
}
