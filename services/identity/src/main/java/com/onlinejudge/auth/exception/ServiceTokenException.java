package com.onlinejudge.auth.exception;

import org.springframework.http.HttpStatus;

/** Errors for the canonical internal API; its body is intentionally not the legacy ApiResponse. */
public class ServiceTokenException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private ServiceTokenException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static ServiceTokenException badRequest(String message) {
        return new ServiceTokenException("SERVICE_TOKEN_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    public static ServiceTokenException identityInvalid() {
        return new ServiceTokenException("SERVICE_IDENTITY_INVALID", "mTLS workload identity is required or invalid", HttpStatus.UNAUTHORIZED);
    }

    public static ServiceTokenException forbidden() {
        return new ServiceTokenException("SERVICE_IDENTITY_FORBIDDEN", "workload identity is not authorized for this audience or scope", HttpStatus.FORBIDDEN);
    }

    public static ServiceTokenException idempotencyConflict() {
        return new ServiceTokenException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was reused with a different request", HttpStatus.CONFLICT);
    }
}
