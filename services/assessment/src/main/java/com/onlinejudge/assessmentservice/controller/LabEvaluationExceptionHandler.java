package com.onlinejudge.assessmentservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** The source-download role gate shares the platform-level access-denied contract. */
@RestControllerAdvice(assignableTypes = LabEvaluationController.class)
class LabEvaluationExceptionHandler {
    @ExceptionHandler(LabAccessDeniedException.class)
    ResponseEntity<Map<String, Object>> accessDenied(LabAccessDeniedException failure, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "ERR-AUTH-05",
                "message", failure.getMessage(),
                "requestId", request.getHeader("X-Request-Id") == null ? "" : request.getHeader("X-Request-Id"),
                "retryable", false));
    }
}
