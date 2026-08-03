package com.flipkart.drift.api.service.utils;

import com.flipkart.drift.api.config.IdempotencyConfig;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.filters.IdempotencyKey;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyKeyResolverTest {

    private static final String HEADER = "X_DRIFT_IDEMPOTENCY_KEY";

    private IdempotencyConfig config(boolean optional, String... headers) {
        IdempotencyConfig config = new IdempotencyConfig();
        config.setHeaders(List.of(headers));
        config.setOptional(optional);
        return config;
    }

    private MultivaluedMap<String, String> headersWith(String name, String value) {
        MultivaluedMap<String, String> headers = new MultivaluedStringMap();
        if (value != null) {
            headers.add(name, value);
        }
        return headers;
    }

    @Test
    void headerAbsentAndOptionalTrueReturnsEmpty() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        Optional<String> result = resolver.extractRawKey(new MultivaluedStringMap());
        assertTrue(result.isEmpty());
    }

    @Test
    void headerAbsentAndOptionalFalseThrows400Missing() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(false, HEADER));
        ApiException ex = assertThrows(ApiException.class,
                () -> resolver.extractRawKey(new MultivaluedStringMap()));
        assertEquals(IdempotencyKeyResolver.ERROR_MISSING, ex.getErrorCode());
    }

    @Test
    void multipleHeadersWithConflictingValuesThrowsAmbiguous() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER, "X_REQUEST_ID"));
        MultivaluedMap<String, String> headers = new MultivaluedStringMap();
        headers.add(HEADER, "key-1");
        headers.add("X_REQUEST_ID", "key-2");

        ApiException ex = assertThrows(ApiException.class, () -> resolver.extractRawKey(headers));
        assertEquals(IdempotencyKeyResolver.ERROR_AMBIGUOUS, ex.getErrorCode());
    }

    @Test
    void malformedKeyThrows400Malformed() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        MultivaluedMap<String, String> headers = headersWith(HEADER, "bad key with spaces!");

        ApiException ex = assertThrows(ApiException.class, () -> resolver.extractRawKey(headers));
        assertEquals(IdempotencyKeyResolver.ERROR_MALFORMED, ex.getErrorCode());
    }

    @Test
    void happyPathResolvesKeyAndWorkflowId() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        MultivaluedMap<String, String> headers = headersWith(HEADER, "order-123");

        Optional<String> rawKey = resolver.extractRawKey(headers);
        assertTrue(rawKey.isPresent());
        assertEquals("order-123", rawKey.get());

        IdempotencyKey key = resolver.resolve("tenant1", "client1", rawKey.get());
        assertTrue(key.getWorkflowId().startsWith("WF-tenant1-client1-"));
    }

    @Test
    void toWorkflowIdIncludesTenantAndClientIdAndSha256() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        String workflowId = resolver.toWorkflowId("Tenant1", "Client1", "raw-key");
        assertTrue(workflowId.startsWith("WF-tenant1-client1-"));
        assertEquals(64, workflowId.substring("WF-tenant1-client1-".length()).length());
    }

    @Test
    void differentClientIdsProduceDifferentWorkflowIds() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        String id1 = resolver.toWorkflowId("tenant1", "client1", "raw-key");
        String id2 = resolver.toWorkflowId("tenant1", "client2", "raw-key");
        assertNotEquals(id1, id2);
    }

    @Test
    void differentTenantsProduceDifferentWorkflowIds() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        String id1 = resolver.toWorkflowId("tenant1", "client1", "raw-key");
        String id2 = resolver.toWorkflowId("tenant2", "client1", "raw-key");
        assertNotEquals(id1, id2);
    }

    @Test
    void differentRawKeysNeverCollide() {
        IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(config(true, HEADER));
        String id1 = resolver.toWorkflowId("tenant1", "client1", "raw-key-a");
        String id2 = resolver.toWorkflowId("tenant1", "client1", "raw-key-b");
        assertNotEquals(id1, id2);
    }
}
