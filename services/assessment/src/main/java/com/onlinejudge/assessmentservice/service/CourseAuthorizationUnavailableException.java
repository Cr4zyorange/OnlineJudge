package com.onlinejudge.assessmentservice.service;

/** The live Course authorization endpoint could not produce a decision; callers fail closed with 503. */
public class CourseAuthorizationUnavailableException extends RuntimeException {
    public CourseAuthorizationUnavailableException(String message) {
        super(message);
    }

    public CourseAuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
