package com.drift.persistence.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.ws.rs.core.Response;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class ExceptionResponse {
    private  String transactionId;
    private Response.Status status;
    private String exceptionClass;
    private String message;

    public ExceptionResponse(String transactionId, Response.Status status, String exceptionClass, String message) {
        this.status = status;
        this.exceptionClass = exceptionClass;
        this.message = message;
        this.transactionId = transactionId;
    }
}

