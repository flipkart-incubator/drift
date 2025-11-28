package com.flipkart.drift.worker.config;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class WorkerDynamicOptions {
    @NotNull
    private Integer workflowTaskPoller;
    @NotNull
    private Integer activityTaskPoller;
    @NotNull
    private Integer workflowCacheSize;
    @NotNull
    private Integer maxWorkflowThreadCount;
}
