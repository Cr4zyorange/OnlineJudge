package com.onlinejudge.courseservice.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CourseExceptionHandler {
    @ExceptionHandler(CourseException.class)
    public ResponseEntity<ErrorResponse> known(CourseException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(
                exception.code(), exception.getMessage(), requestId(request), exception.retryable()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(
                "COURSE_INTERNAL_ERROR", "course service could not complete the request", requestId(request), false));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("course.requestId");
        return value == null ? "" : value.toString();
    }
}
