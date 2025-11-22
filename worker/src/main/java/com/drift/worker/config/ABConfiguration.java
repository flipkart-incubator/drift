package com.drift.worker.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ABConfiguration {
    @NotNull
    @JsonProperty("isABEnabled")
    private Boolean isABEnabled;
    @NotNull
    @JsonProperty("clientId")
    private String clientId;
    
    @NotNull
    @JsonProperty("tenantId")
    private String tenantId;
    
    @NotNull
    @JsonProperty("endpoint")
    private String endpoint;
    
    @NotNull
    @JsonProperty("clientSecretKey")
    private String clientSecretKey;
} 