package com.onlinejudge.assessmentservice.security;

import org.springframework.http.HttpStatus;

public final class ServiceIdentityException extends RuntimeException {
    private final String code; private final HttpStatus status;
    private ServiceIdentityException(String code, HttpStatus status, String message) { super(message); this.code = code; this.status = status; }
    public static ServiceIdentityException invalid() { return new ServiceIdentityException("SERVICE_IDENTITY_INVALID", HttpStatus.UNAUTHORIZED, "service identity is missing or invalid"); }
    public static ServiceIdentityException forbidden() { return new ServiceIdentityException("SERVICE_IDENTITY_FORBIDDEN", HttpStatus.FORBIDDEN, "service identity lacks grades:read"); }
    public String code() { return code; } public HttpStatus status() { return status; }
}
