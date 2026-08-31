package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.security.ServiceIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AssessmentSourceGradeController.class)
class AssessmentInternalApiExceptionHandler {
    @ExceptionHandler(SourceGradeRequestException.class)
    ResponseEntity<Map<String, Object>> badRequest(SourceGradeRequestException failure, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(Map.of("code", "SOURCE_GRADE_FILTER_INVALID", "message", failure.getMessage(), "requestId", request.getHeader("X-Request-Id") == null ? "" : request.getHeader("X-Request-Id"), "retryable", false));
    }
    @ExceptionHandler(SourceGradeSnapshotExpiredException.class)
    ResponseEntity<Map<String, Object>> expiredSnapshot(SourceGradeSnapshotExpiredException failure, HttpServletRequest request) {
        return ResponseEntity.status(409).body(Map.of("code", "SOURCE_GRADE_SNAPSHOT_EXPIRED", "message", failure.getMessage(), "requestId", request.getHeader("X-Request-Id") == null ? "" : request.getHeader("X-Request-Id"), "retryable", true));
    }
    @ExceptionHandler(ServiceIdentityException.class)
    ResponseEntity<Map<String, Object>> serviceIdentity(ServiceIdentityException failure, HttpServletRequest request) {
        return ResponseEntity.status(failure.status()).body(Map.of("code", failure.code(), "message", failure.getMessage(), "requestId", request.getHeader("X-Request-Id") == null ? "" : request.getHeader("X-Request-Id"), "retryable", false));
    }
}
