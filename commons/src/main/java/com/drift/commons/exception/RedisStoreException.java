package com.drift.commons.exception;


import javax.ws.rs.core.Response;

public class RedisStoreException extends ApiException {

    public RedisStoreException(Response.Status status, String message, String errorCode) {
        super(status, message, errorCode);
    }
}
