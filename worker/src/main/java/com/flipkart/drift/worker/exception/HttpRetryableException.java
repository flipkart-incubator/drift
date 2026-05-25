package com.flipkart.drift.worker.exception;

/**
 * Thrown by {@link com.flipkart.drift.worker.executor.HttpExecutor} when an HTTP response
 * indicates a transient failure that should be retried by Temporal:
 * <ul>
 *   <li>5xx (500–599) — server-side errors</li>
 *   <li>408 — Request Timeout</li>
 *   <li>429 — Too Many Requests</li>
 * </ul>
 * All other non-2xx responses (4xx except 408/429) are non-retryable and should
 * cause the workflow to route to its failure node instead.
 */
public class HttpRetryableException extends RuntimeException {

    public HttpRetryableException(String message) {
        super(message);
    }

    public HttpRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
