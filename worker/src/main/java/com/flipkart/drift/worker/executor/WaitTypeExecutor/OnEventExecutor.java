package com.flipkart.drift.worker.executor.WaitTypeExecutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OnEventExecutor implements WaitTypeExecutor {

    @Override
    public ActivityResponse executeWait(ActivityRequest<WaitNode> activityRequest) {
        OnEventConfig config = activityRequest.getNodeDefinition().getTypedConfig(OnEventConfig.class);
        List<String> resolvedEventTypes = resolveExpectedEventTypes(config, activityRequest);
        log.info("WfId: {} parking at ON_EVENT wait, resolvedExpectedEventTypes: {}, waitSemantics: {}",
                activityRequest.getWorkflowId(), resolvedEventTypes, config.getWaitSemantics());
        return ActivityResponse.builder()
                .workflowStatus(WorkflowStatus.WAITING)
                .resolvedExpectedEventTypes(resolvedEventTypes)
                .build();
    }

    private List<String> resolveExpectedEventTypes(OnEventConfig config, ActivityRequest<WaitNode> activityRequest) {
        if (config.getExpectedEventTypesVar() != null && !config.getExpectedEventTypesVar().isEmpty()) {
            return resolveFromContext(config.getExpectedEventTypesVar(), activityRequest.getContext(),
                    activityRequest.getWorkflowId());
        }
        if (config.getExpectedEventTypes() == null || config.getExpectedEventTypes().isEmpty()) {
            throw Activity.wrap(new IllegalStateException(
                    "OnEventConfig must have either expectedEventTypesVar or expectedEventTypes"));
        }
        return config.getExpectedEventTypes();
    }

    // Navigates a dot-path (e.g. "fetchVendors.vendorIds") into the HBase context and returns the value as List<String>.
    public static List<String> resolveFromContext(String dotPath, JsonNode context, String workflowId) {
        String[] parts = dotPath.split("\\.", 2);
        JsonNode node = context.get(parts[0]);
        if (node == null) {
            throw Activity.wrap(new IllegalStateException(
                    "WfId: " + workflowId + " expectedEventTypesVar '" + dotPath + "': key '" + parts[0] + "' not found in context"));
        }
        if (parts.length == 2) {
            node = node.get(parts[1]);
            if (node == null) {
                throw Activity.wrap(new IllegalStateException(
                        "WfId: " + workflowId + " expectedEventTypesVar '" + dotPath + "': field '" + parts[1] + "' not found"));
            }
        }
        if (!node.isArray()) {
            throw Activity.wrap(new IllegalStateException(
                    "WfId: " + workflowId + " expectedEventTypesVar '" + dotPath + "' must point to a JSON array, got: " + node.getNodeType()));
        }
        List<String> result = new ArrayList<>();
        node.forEach(el -> result.add(el.asText()));
        if (result.isEmpty()) {
            log.warn("WfId: {} expectedEventTypesVar '{}' resolved to empty list", workflowId, dotPath);
        }
        return result;
    }
}
