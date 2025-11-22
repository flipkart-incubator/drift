package com.drift.api.exception;

import lombok.Getter;
import lombok.Setter;

import javax.ws.rs.core.Response;

@Getter
@Setter
public class ApiException extends RuntimeException {
    private final Response.Status status;
    private final String message;
    private String description;
    private String errorCode;

    public ApiException(Response.Status status, String message) {
        super(message);
        this.status = status;
        this.message = message;
    }

    public ApiException(Response.Status status, String message, String errorCode) {
        super(message);
        this.status = status;
        this.message = message;
        this.errorCode = errorCode;
    }
}




