package com.drift.worker.exception;

public class GroovyException extends RuntimeException {

    public GroovyException(Throwable cause) {
        super(cause);
    }

    public GroovyException(String message, Throwable cause) {
        super(message, cause);
    }
}
