package com.drift.worker.Utility;

import com.flipkart.abservice.models.request.ABExperimentRequest;
import com.flipkart.abservice.models.response.ABVariable;
import com.flipkart.abservice.models.response.ABVariableResponse;
import com.flipkart.abservice.pojo.UserID;
import com.flipkart.abservice.resources.ABService;
import com.drift.worker.config.ABConfiguration;
import lombok.extern.slf4j.Slf4j;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.temporal.activity.Activity;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.Stopwatch;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class ABHelper {
    private final String abClientId;

    @Inject
    public ABHelper(ABConfiguration abConfiguration) {
        this.abClientId = abConfiguration.getClientId();
    }

    public ABVariableResponse getABV2Response(String userId, String experimentName) {
        Scope metricsScope = Activity.getExecutionContext().getMetricsScope();

        Map<String, String> tags = new HashMap<>();
        tags.put("experiment", experimentName);
        Scope requestScope = metricsScope.tagged(tags);
        requestScope.counter("ab_requests_total").inc(1);

        // Start the timer
        Stopwatch stopwatch = requestScope.timer("ab_request_duration_ms").start();
        
        try {
            UserID userID = new UserID(userId, null);
            ABExperimentRequest abRequest = new ABExperimentRequest(userID, experimentName);
            abRequest.setClientID(abClientId);
            return ABService.getInstance().getABVariables(abRequest);
        } catch (Exception e) {
            requestScope.counter("ab_requests_exception").inc(1);
            log.error("Error calling ABService for user {} experiment {}: {}", 
                      userId, experimentName, e.getMessage(), e);
            return null;
        } finally {
            // Stop the timer
            stopwatch.stop();
        }
    }

    public boolean isCustomerInTreatment(String userId, String experimentName, String variableName) {
        ABVariableResponse abResponse = getABV2Response(userId, experimentName);
        if (abResponse != null && abResponse.getAbId() != null) {
            Map<String, Object> abVariable = abResponse.getAllVariables();
            Boolean variableValue = (Boolean) abVariable.get(variableName);
            return Boolean.TRUE.equals(variableValue);
        }
        return false;
    }

}
