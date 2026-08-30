package com.onlinejudge.common.reliability;

/** A schema, version or authorization violation that must be isolated in the DLQ. */
public class NonRetryableEventException extends RuntimeException {
    public NonRetryableEventException(String message) {
        super(message);
    }
}
