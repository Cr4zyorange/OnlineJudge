package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import com.onlinejudge.assessmentservice.service.HomeworkPublicationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** User-facing HWK errors use the shared v2 envelope {code, message, requestId, retryable}. */
@RestControllerAdvice(assignableTypes = { HomeworkController.class, HomeworkEvaluationController.class })
class HomeworkApiExceptionHandler {
    @ExceptionHandler(HomeworkPublicationException.class)
    ResponseEntity<Map<String, Object>> publicationFailed(HomeworkPublicationException failure, HttpServletRequest request) {
        return error(HttpStatusCode.valueOf(503), "HWK_5003", failure.getMessage(), true, request);
    }

    @ExceptionHandler(CourseAuthorizationUnavailableException.class)
    ResponseEntity<Map<String, Object>> courseAuthorizationUnavailable(CourseAuthorizationUnavailableException failure,
            HttpServletRequest request) {
        return error(HttpStatusCode.valueOf(503), "COURSE_AUTHORIZATION_UNAVAILABLE", failure.getMessage(), true, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException failure, HttpServletRequest request) {
        HttpStatusCode status = failure.getStatusCode();
        String message = failure.getReason() == null ? "request failed" : failure.getReason();
        return error(status, codeFor(status), message, status.value() >= 500, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadableBody(HttpMessageNotReadableException failure, HttpServletRequest request) {
        return error(HttpStatusCode.valueOf(400), "HWK_4000", "request body is not valid JSON", false, request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> unsupportedMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException failure, HttpServletRequest request) {
        return error(HttpStatusCode.valueOf(415), "HWK_4005", "request media type is not supported", false, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidArgument(MethodArgumentNotValidException failure, HttpServletRequest request) {
        return error(HttpStatusCode.valueOf(400), "HWK_4000", "request fields are invalid", false, request);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatusCode status, String code, String message,
            boolean retryable, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("requestId", request.getHeader("X-Request-Id") == null ? "" : request.getHeader("X-Request-Id"));
        body.put("retryable", retryable);
        return ResponseEntity.status(status).body(body);
    }

    private static String codeFor(HttpStatusCode status) {
        int value = status.value();
        if (value == 400) return "HWK_4000";
        if (value == 403) return "HWK_4003";
        if (value == 404) return "HWK_4004";
        if (value == 409) return "HWK_4009";
        if (value == 415) return "HWK_4005";
        return value >= 500 ? "HWK_5000" : "HWK_4000";
    }
}
