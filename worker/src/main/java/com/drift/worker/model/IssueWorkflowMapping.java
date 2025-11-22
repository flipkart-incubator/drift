package com.drift.worker.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueWorkflowMapping {

    private String workflowId;

    private boolean abEnabled;

    private ABDetails abMetaDetails;

    private String defaultVersion;
    

    public boolean hasValidConfig() {
        return workflowId != null && defaultVersion != null && !defaultVersion.isEmpty();
    }

    public boolean hasValidABConfig() {
        return abEnabled && abMetaDetails != null;
    }

}