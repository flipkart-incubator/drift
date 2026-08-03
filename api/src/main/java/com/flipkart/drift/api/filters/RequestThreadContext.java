package com.flipkart.drift.api.filters;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class RequestThreadContext {
    private static ThreadLocal<RequestThreadContext> threadLocal = ThreadLocal.withInitial(RequestThreadContext::new);
    private String clientId;
    private Boolean perfFlag;
    private String tenant;
    private String username;
    private String resolvedWorkflowId;
    private String idempotencyKey;
    private boolean resolvedFromExistingWorkflow;

    public static RequestThreadContext get() {
        return threadLocal.get();
    }

    public static void remove() {
        threadLocal.remove();
    }

    public void clear() {
        clientId = null;
        tenant = null;
        perfFlag = null;
        username = null;
        resolvedWorkflowId = null;
        idempotencyKey = null;
        resolvedFromExistingWorkflow = false;
    }

    public Map<String, String> getLegacyThreadContext() {
        Map<String, String> threadContext = new HashMap<>();
        threadContext.put("tenant", tenant);
        threadContext.put("userName", username);
        threadContext.put("clientId", clientId);
        threadContext.put("perfFlag", String.valueOf(perfFlag));
        threadContext.put("idempotencyKey", idempotencyKey);
        return threadContext;
    }
}




