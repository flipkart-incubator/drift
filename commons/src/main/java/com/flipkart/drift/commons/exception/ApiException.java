package com.flipkart.drift.commons.exception;

import lombok.Data;

import javax.ws.rs.core.Response;

@Data
public class ApiException extends RuntimeException{
    private Response.Status status;
    private String message;
    private String errorCode;

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

    public ApiException(Response.Status status, String message, String errorCode) {
        super(message);
        this.status = status;
        this.message = message;
        this.errorCode = errorCode;
    }

}
