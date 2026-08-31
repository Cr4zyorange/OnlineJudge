package com.onlinejudge.lrn.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Renders Learning's internal v2 errors with the documented contract shape. */
@RestControllerAdvice(assignableTypes = LearningTaskInternalController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LearningInternalV2ExceptionHandler {

    @ExceptionHandler(InternalV2RequestException.class)
    public ResponseEntity<InternalV2Error> requestId(InternalV2RequestException exception, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return ResponseEntity.status(exception.status()).body(new InternalV2Error(
                exception.code(), exception.getMessage(), requestId == null ? "" : requestId, exception.retryable()));
    }
}
