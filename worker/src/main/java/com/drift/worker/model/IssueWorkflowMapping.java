package com.drift.worker.model;

import lombok.Builder;
import lombok.Data;

/**
 * Model for issue to workflow mapping with optional A/B testing configuration.
 */
@Data
@Builder
public class IssueWorkflowMapping {

    private String workflowId;
    private String defaultVersion;
    
    // A/B Testing fields (flattened)
    private boolean abEnabled;
    private String experimentName;
    private String pivotType;
    private String variable;
    private String controlVersion;
    private String treatmentVersion;

    /**
     * Check if the mapping has valid basic configuration.
     */
    public boolean hasValidConfig() {
        return workflowId != null && defaultVersion != null && !defaultVersion.isEmpty();
    }

    /**
     * Check if the mapping has valid A/B testing configuration.
     */
    public boolean hasValidABConfig() {
        return abEnabled && experimentName != null && pivotType != null && variable != null;
    }
}
