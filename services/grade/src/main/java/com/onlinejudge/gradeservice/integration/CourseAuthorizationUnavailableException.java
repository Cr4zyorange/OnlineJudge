package com.onlinejudge.gradeservice.integration;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CourseAuthorizationUnavailableException extends ResponseStatusException {
    public CourseAuthorizationUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public CourseAuthorizationUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
