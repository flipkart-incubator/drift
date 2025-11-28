package com.flipkart.drift.worker.Utility;

import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;

@Slf4j
public class NodeParameterEvaluator {
    public static ObjectNode evaluateNodeParameters(ObjectNode context, Map<String, String> parameters) {
        ObjectNode result = MAPPER.createObjectNode();
        if (context == null || parameters == null || context.isEmpty() || parameters.isEmpty()) {
            return result;
        }
        String contextString = context.toString();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            String valueOrJsonPath = entry.getValue();

            try {
                if (valueOrJsonPath.startsWith("$.")) { // Treat as JSONPath
                    Object value = JsonPath.read(contextString, valueOrJsonPath);
                    result.set(paramName, MAPPER.valueToTree(value));
                } else { // Treat as a static value
                    result.put(paramName, valueOrJsonPath);
                }
            } catch (Exception e) {
                result.putNull(paramName);
                log.error("Error evaluating parameter: {} with value: {}", paramName, valueOrJsonPath, e);
            }
        }

        return result;
    }
}
