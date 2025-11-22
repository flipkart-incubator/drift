package com.drift.worker.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.drift.persistence.bootstrap.CacheMaxEntriesConfig;
import com.drift.persistence.bootstrap.StaticCacheRefreshConfig;
import io.dropwizard.Configuration;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriftWorkerConfiguration extends Configuration {
    @NotNull
    private RedisConfiguration redisConfiguration;
    @NotNull
    private ExecutorServiceConfig cacheRefreshExecutorServiceConfig;
    @NotNull
    private StaticCacheRefreshConfig staticCacheRefreshConfig;
    @NotNull
    private CacheMaxEntriesConfig cacheMaxEntriesConfig;
    @NotNull
    private String hbaseConfigBucket;
    @NotNull
    private String enumStoreBucket;
    @NotNull
    private String temporalFrontEnd;
    @NotNull
    private PrometheusConfig prometheusConfig;
    @NotNull
    private Long awaitTerminationTimeoutInSec;
    @NotNull
    private WorkerDynamicOptions workerDynamicOptions;
    @NotNull
    private ABConfiguration abConfiguration;

    private String temporalTaskQueue;

    // Optional Hadoop identity parameters, used when connecting to HBase
    private String hadoopUserName;
    private String hadoopLoginUser;

    // Authentication configuration for AuthN service
    @NotNull
    private String authClientName;
    @NotNull
    private String authClientUrl;
    @NotNull
    private String authClientSecret;
}
