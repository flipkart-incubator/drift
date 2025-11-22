package com.drift.persistence.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.ws.rs.core.Response;

@EqualsAndHashCode(callSuper = true)
@Data
public class ApiException extends RuntimeException{
    private Response.Status status;
    private String message;

    public ApiException() {
        super();
    }

    public ApiException(String message) {
        super(message);
        this.message=message;
        status=Response.Status.INTERNAL_SERVER_ERROR;
    }

    public ApiException(String message, Response.Status status) {
        super(message);
        this.message=message;
        this.status=status;
    }

    public ApiException(Response.Status status,String message) {
        super(message);
        this.message=message;
        this.status=status;
    }

    public ApiException(String message, Throwable cause) {
        super(message,cause);
        this.message=message;
        this.status=Response.Status.INTERNAL_SERVER_ERROR;
    }

    public ApiException(String message, Response.Status status, Throwable cause) {
        super(message, cause);
        this.message=message;
        this.status=status;
    }

}

