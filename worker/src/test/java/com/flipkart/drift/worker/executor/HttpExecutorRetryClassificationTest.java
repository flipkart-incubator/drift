package com.flipkart.drift.worker.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HttpExecutor#isRetryableStatusCode(int)}.
 * Tests the Harmony-parity classification: 5xx + 408 + 429 are retryable;
 * all other 4xx are non-retryable.
 */
class HttpExecutorRetryClassificationTest {

    // ---- Retryable: 5xx ----

    @Test
    void statusCode500_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(500));
    }

    @Test
    void statusCode502_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(502));
    }

    @Test
    void statusCode503_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(503));
    }

    @Test
    void statusCode504_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(504));
    }

    @Test
    void statusCode599_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(599));
    }

    // ---- Retryable: special 4xx ----

    @Test
    void statusCode408_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(408));
    }

    @Test
    void statusCode429_isRetryable() {
        assertTrue(HttpExecutor.isRetryableStatusCode(429));
    }

    // ---- Non-retryable: standard 4xx ----

    @Test
    void statusCode400_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(400));
    }

    @Test
    void statusCode401_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(401));
    }

    @Test
    void statusCode403_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(403));
    }

    @Test
    void statusCode404_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(404));
    }

    @Test
    void statusCode422_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(422));
    }

    @Test
    void statusCode409_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(409));
    }

    // ---- Boundary: 600 is not 5xx ----

    @Test
    void statusCode600_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(600));
    }

    // ---- Boundary: 499 is not retryable (not 408 or 429) ----

    @Test
    void statusCode499_isNotRetryable() {
        assertFalse(HttpExecutor.isRetryableStatusCode(499));
    }
}
