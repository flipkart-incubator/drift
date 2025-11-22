package com.drift.worker.config;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class PrometheusConfig {
    @NotNull
    private Integer port;
    @NotNull
    private String path;
}
