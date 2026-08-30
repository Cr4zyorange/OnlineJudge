package com.onlinejudge.courseservice.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class CourseExceptionHandler {
    @ExceptionHandler(CourseException.class)
    public ResponseEntity<ErrorResponse> known(CourseException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(
                exception.code(), exception.getMessage(), requestId(request), exception.retryable()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "COURSE_REQUEST_INVALID", "course request is invalid", requestId(request), false));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse(
                "COURSE_MEDIA_TYPE_UNSUPPORTED", "course request media type is unsupported", requestId(request), false));
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
