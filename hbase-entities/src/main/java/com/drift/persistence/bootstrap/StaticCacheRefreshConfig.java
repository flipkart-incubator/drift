package com.drift.persistence.bootstrap;

import lombok.Data;

@Data
public class StaticCacheRefreshConfig {
    private Long issue;
    private Long disposition;
    private Long queue;
    private Long profile;
    private Long statusWithType;
    private Long collectionLog;
    private Long channel;
    private Long threadEntryType;
    private Long similarityRule;
    private Long fieldMeta;
    private Long stateMachine;
    private Long vachanConfig;
    private Long formTemplateConfig;
    private Long notesTemplateConfig;
    private Long planConfig;
    private Long reasonConfig;

    private Long nodeDefinitionConfig;
    private Long workflowConfig;
}

