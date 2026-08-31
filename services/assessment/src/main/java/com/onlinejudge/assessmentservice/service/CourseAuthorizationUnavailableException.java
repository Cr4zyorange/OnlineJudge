package com.onlinejudge.assessmentservice.service;

/** Signals that CRS could not provide an authorization decision; this is retryable, not a denial. */
public final class CourseAuthorizationUnavailableException extends RuntimeException {
    public CourseAuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public CourseAuthorizationUnavailableException(String message) {
        super(message);
    }
}
