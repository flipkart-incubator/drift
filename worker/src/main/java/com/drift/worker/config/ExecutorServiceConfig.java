package com.drift.worker.config;

import lombok.Data;

@Data
public class ExecutorServiceConfig {
    private int minThreads;
    private int maxThreads;
    private int queueSize;
}
