package com.flipkart.drift.worker.service;

import com.flipkart.drift.worker.Utility.ABServiceInitializer;
import com.flipkart.drift.sdk.spi.ab.ABTestingProvider;
import com.flipkart.drift.sdk.spi.ab.ABTestingProviderFactory;
import com.flipkart.drift.worker.model.IssueWorkflowMapping;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.config.DynamicProperty;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Service for mapping issue IDs to workflow configurations.
 * Uses DynamicProperty for configuration and pluggable A/B testing.
 */
@Slf4j
@Singleton
public class IssueWorkflowMappingService {
    private static final String WORKFLOW_KEY_FORMAT = "workflow.%s.key";
    private static final String WORKFLOW_VERSION_FORMAT = "workflow.%s.version";
    private static final String WORKFLOW_AB_ENABLED_FORMAT = "workflow.%s.ab.enabled";
    private static final String WORKFLOW_AB_EXPERIMENT_FORMAT = "workflow.%s.ab.experimentName";
    private static final String WORKFLOW_AB_PIVOT_TYPE_FORMAT = "workflow.%s.ab.pivotType";
    private static final String WORKFLOW_AB_VARIABLE_FORMAT = "workflow.%s.ab.variable";
    private static final String WORKFLOW_AB_CONTROL_VERSION_FORMAT = "workflow.%s.ab.control.version";
    private static final String WORKFLOW_AB_TREATMENT_VERSION_FORMAT = "workflow.%s.ab.treatment.version";
    
    private final ABTestingProvider abTestingProvider;
    
    @Inject
    public IssueWorkflowMappingService(ABServiceInitializer abServiceInitializer) {
        // Get the ABTestingProvider from the initialized ABServiceInitializer
        this.abTestingProvider = abServiceInitializer.getProvider();
        log.debug("Using ABTestingProvider from ABServiceInitializer: {}", 
                 abTestingProvider.getClass().getSimpleName());
    }

    /**
     * Get workflow mapping for a specific issue ID.
     * 
     * @param issueId The issue ID
     * @return IssueWorkflowMapping or null if not found
     */
    public IssueWorkflowMapping getIssueWorkflowMappingForIssue(String issueId) {
        if (issueId == null || issueId.trim().isEmpty()) {
            log.debug("Issue ID is null or empty");
            return null;
        }
        
        try {
            String workflowId = DynamicProperty.getInstance(String.format(WORKFLOW_KEY_FORMAT, issueId)).getString();
            String defaultVersion = DynamicProperty.getInstance(String.format(WORKFLOW_VERSION_FORMAT, issueId)).getString();
            
            if (workflowId == null || workflowId.trim().isEmpty()) {
                log.info("No workflow mapping found for issue ID: {}", issueId);
                return null;
            }
            
            // Check if AB testing is enabled
            Boolean abEnabled = DynamicProperty.getInstance(String.format(WORKFLOW_AB_ENABLED_FORMAT, issueId)).getBoolean(false);
            
            IssueWorkflowMapping.IssueWorkflowMappingBuilder builder = IssueWorkflowMapping.builder()
                    .workflowId(workflowId)
                    .defaultVersion(defaultVersion)
                    .abEnabled(abEnabled);
            
            // Load AB configuration if enabled
            if (abEnabled) {
                String experimentName = DynamicProperty.getInstance(String.format(WORKFLOW_AB_EXPERIMENT_FORMAT, issueId)).getString();
                String pivotType = DynamicProperty.getInstance(String.format(WORKFLOW_AB_PIVOT_TYPE_FORMAT, issueId)).getString();
                String variable = DynamicProperty.getInstance(String.format(WORKFLOW_AB_VARIABLE_FORMAT, issueId)).getString();
                String controlVersion = DynamicProperty.getInstance(String.format(WORKFLOW_AB_CONTROL_VERSION_FORMAT, issueId)).getString();
                String treatmentVersion = DynamicProperty.getInstance(String.format(WORKFLOW_AB_TREATMENT_VERSION_FORMAT, issueId)).getString();
                
                if (experimentName != null && pivotType != null && variable != null) {
                    builder.experimentName(experimentName)
                           .pivotType(pivotType)
                           .variable(variable)
                           .controlVersion(controlVersion != null ? controlVersion : defaultVersion)
                           .treatmentVersion(treatmentVersion != null ? treatmentVersion : defaultVersion);
                } else {
                    log.warn("AB enabled for issue {} but missing required config (experimentName, pivotType, variable)", issueId);
                    builder.abEnabled(false); // Disable AB if config is incomplete
                }
            }
            
            return builder.build();
        } catch (Exception e) {
            log.error("Error extracting workflow mapping for issue ID: {}", issueId, e);
            return null;
        }
    }

    /**
     * Get workflow and version for a workflow start request, considering A/B testing.
     * 
     * @param workflowStartRequest The workflow start request
     * @param mapping The issue workflow mapping
     * @return Map with workflowId and version, or null if mapping is invalid
     */
    public Map<String, String> getWorkflowMapping(WorkflowStartRequest workflowStartRequest, IssueWorkflowMapping mapping) {
        if (mapping == null) {
            log.debug("Mapping is null");
            return null;
        }
        
        String issueId = workflowStartRequest.getIssueDetail() != null ? 
                        workflowStartRequest.getIssueDetail().getIssueId() : "unknown";
        
        Map<String, String> result = new HashMap<>();
        result.put("workflowId", mapping.getWorkflowId());
        
        // Determine version based on A/B testing
        String version = determineVersion(workflowStartRequest, mapping);
        result.put("version", version);
        
        log.info("Workflow mapping for issue {}: workflowId={}, version={}", 
                issueId, mapping.getWorkflowId(), version);
        
        return result;
    }
    
    /**
     * Determine which version to use based on A/B testing configuration.
     * 
     * @param workflowStartRequest The workflow start request
     * @param mapping The issue workflow mapping
     * @return The version to use
     */
    private String determineVersion(WorkflowStartRequest workflowStartRequest, IssueWorkflowMapping mapping) {
        // If AB not enabled, return default version
        if (!mapping.isAbEnabled()) {
            return mapping.getDefaultVersion();
        }
        
        try {
            // Extract pivot value from request params based on pivot type
            String pivotValue = extractPivotValue(workflowStartRequest, mapping.getPivotType());
            
            if (pivotValue == null) {
                log.warn("Could not extract pivot value for type: {}, using default version", mapping.getPivotType());
                return mapping.getDefaultVersion();
            }
            
            // Use AB testing provider to determine bucket
            boolean isInTreatment = abTestingProvider.isInTreatment(
                    pivotValue, 
                    mapping.getExperimentName(), 
                    mapping.getVariable()
            );
            
            String version = isInTreatment ? mapping.getTreatmentVersion() : mapping.getControlVersion();
            
            // Fall back to default if version is null
            if (version == null) {
                log.warn("AB test returned null version, using default");
                version = mapping.getDefaultVersion();
            }
            
            log.info("AB Test result - pivot: {}, experiment: {}, variable: {}, bucket: {}, version: {}",
                    pivotValue, mapping.getExperimentName(), mapping.getVariable(), 
                    isInTreatment ? "TREATMENT" : "CONTROL", version);
            
            return version;
        } catch (Exception e) {
            log.error("Error in A/B testing, falling back to default version", e);
            return mapping.getDefaultVersion();
        }
    }
    
    /**
     * Extract pivot value from workflow request based on pivot type.
     * 
     * @param workflowStartRequest The workflow start request
     * @param pivotType The type of pivot (e.g., "customerId", "orderId")
     * @return The pivot value or null if not found
     */
    private String extractPivotValue(WorkflowStartRequest workflowStartRequest, String pivotType) {
        if (pivotType == null) {
            return null;
        }
        
        // Try to get from customer first (backward compatibility)
        if ("customerId".equals(pivotType) && workflowStartRequest.getCustomer() != null) {
            return workflowStartRequest.getCustomer().getCustomerId();
        }
        
        // Try to get from params (new standardized way)
        if (workflowStartRequest.getParams() != null) {
            Object value = workflowStartRequest.getParams().get(pivotType);
            if (value != null) {
                return value.toString();
            }
        }
        
        log.debug("Pivot value not found for type: {}", pivotType);
        return null;
    }
}
