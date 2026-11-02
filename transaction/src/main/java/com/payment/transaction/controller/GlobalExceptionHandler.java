package com.payment.transaction.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * Converts a {@link RejectedExecutionException} into a clean, fast
 * {@code 503 Service Unavailable} instead of letting it surface as a
 * generic {@code 500}. This is the "server is overloaded, retry later"
 * signal — distinct from an actual bug.
 *
 * <p>Note: since {@code paymentExecutor} moved to a virtual-thread-per-task
 * executor (no fixed pool size/queue), this exception is no longer expected
 * to be thrown from that executor in practice — kept as defense-in-depth
 * in case a bounded executor is reintroduced elsewhere.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<Map<String, String>> handleRejectedExecution(RejectedExecutionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Server is overloaded, please retry"));
    }
}
