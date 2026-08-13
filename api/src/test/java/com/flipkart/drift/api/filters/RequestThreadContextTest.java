package com.flipkart.drift.api.filters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestThreadContextTest {

    @AfterEach
    void tearDown() {
        RequestThreadContext.remove();
    }

    @Test
    void clearResetsAllFieldsIncludingIdempotencyFields() {
        RequestThreadContext ctx = RequestThreadContext.get();
        ctx.setClientId("client-1");
        ctx.setTenant("tenant-1");
        ctx.setUsername("user-1");
        ctx.setPerfFlag(true);
        ctx.setResolvedWorkflowId("WF-tenant-client-abc123");
        ctx.setIdempotencyKey("raw-key");
        ctx.setResolvedFromExistingWorkflow(true);

        ctx.clear();

        assertNull(ctx.getClientId());
        assertNull(ctx.getTenant());
        assertNull(ctx.getUsername());
        assertNull(ctx.getPerfFlag());
        assertNull(ctx.getResolvedWorkflowId());
        assertNull(ctx.getIdempotencyKey());
        assertFalse(ctx.isResolvedFromExistingWorkflow());
    }

    @Test
    void legacyThreadContextIncludesIdempotencyKeyOnlyNotResolvedFields() {
        RequestThreadContext ctx = RequestThreadContext.get();
        ctx.clear();
        ctx.setTenant("tenant-1");
        ctx.setUsername("user-1");
        ctx.setClientId("client-1");
        ctx.setIdempotencyKey("raw-key");
        ctx.setResolvedWorkflowId("WF-tenant-client-abc123");
        ctx.setResolvedFromExistingWorkflow(true);

        Map<String, String> legacy = ctx.getLegacyThreadContext();

        assertEquals("raw-key", legacy.get("idempotencyKey"));
        assertFalse(legacy.containsKey("resolvedWorkflowId"));
        assertFalse(legacy.containsKey("resolvedFromExistingWorkflow"));
    }
}
