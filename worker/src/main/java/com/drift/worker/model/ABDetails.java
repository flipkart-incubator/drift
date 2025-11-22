package com.drift.worker.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;


@Data
@Builder
public class ABDetails {
    @NonNull
    private String issueId;

    @NonNull
    private String workflowId;

    @NonNull
    private String experimentName;
    
    @NonNull
    private String pivotType;
    
    @NonNull
    private String variable;
    
    private ABConfig control;
    
    private ABConfig treatment;


    public boolean isValid() {
        return issueId != null && !issueId.trim().isEmpty() &&
               workflowId != null && !workflowId.trim().isEmpty() &&
               experimentName != null && !experimentName.trim().isEmpty() &&
               pivotType != null && !pivotType.trim().isEmpty() &&
               variable != null && !variable.trim().isEmpty() &&
               control != null && treatment != null;
    }
    

    @Data
    @Builder
    public static class ABConfig {
        private String version;
        
        public boolean isValid() {
            return version != null && !version.trim().isEmpty();
        }
    }
}