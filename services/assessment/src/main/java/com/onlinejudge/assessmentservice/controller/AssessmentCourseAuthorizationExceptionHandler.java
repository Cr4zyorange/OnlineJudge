package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Keeps CRS outages distinguishable from an explicit course-management denial. */
@RestControllerAdvice
class AssessmentCourseAuthorizationExceptionHandler {
    @ExceptionHandler(CourseAuthorizationUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(CourseAuthorizationUnavailableException failure, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "COURSE_AUTHORIZATION_UNAVAILABLE",
                "message", failure.getMessage(),
                "requestId", request.getHeader("X-Request-Id") == null ? "" : request.getHeader("X-Request-Id"),
                "retryable", true));
    }
}
