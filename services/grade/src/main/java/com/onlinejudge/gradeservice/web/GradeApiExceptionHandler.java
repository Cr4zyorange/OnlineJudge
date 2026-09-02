package com.onlinejudge.gradeservice.web;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.service.GradeAdjustmentException;
import com.onlinejudge.grd.service.GradeItemNotFoundException;
import com.onlinejudge.grd.service.GradeItemPermissionException;
import com.onlinejudge.grd.service.GradePublishException;
import com.onlinejudge.grd.service.GradeReviewDuplicateException;
import com.onlinejudge.grd.service.GradeReviewPermissionException;
import com.onlinejudge.grd.service.GradeReviewValidationException;
import com.onlinejudge.grd.service.InvalidGradeRuleException;
import com.onlinejudge.grd.service.StudentGradeAccessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issue #367 API coverage: the independent Grade service must expose the frozen
 * error-code contract instead of Spring's default 500 body.  The reviewed
 * monolith ApiExceptionHandler owns the identical mapping; this advice restores
 * that public behavior in the Grade artifact without touching paths or payloads.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GradeApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GradeApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(GradeItemPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleGradePermission(GradeItemPermissionException exception) {
        return forbidden("ERR-GRD-01", exception);
    }

    @ExceptionHandler(StudentGradeAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleStudentGradeAccess(StudentGradeAccessException exception) {
        return forbidden("ERR-GRD-02", exception);
    }

    @ExceptionHandler(GradeReviewPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewPermission(GradeReviewPermissionException exception) {
        return forbidden("ERR-GRD-09", exception);
    }

    @ExceptionHandler(InvalidGradeRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRule(InvalidGradeRuleException exception) {
        return badRequest("ERR-GRD-03", exception);
    }

    @ExceptionHandler(GradePublishException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublish(GradePublishException exception) {
        return badRequest("ERR-GRD-04", exception);
    }

    @ExceptionHandler(GradeReviewValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewValidation(GradeReviewValidationException exception) {
        return badRequest("ERR-GRD-04", exception);
    }

    @ExceptionHandler(GradeAdjustmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleAdjustment(GradeAdjustmentException exception) {
        return badRequest("ERR-GRD-06", exception);
    }

    @ExceptionHandler(GradeReviewDuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewDuplicate(GradeReviewDuplicateException exception) {
        return badRequest("ERR-GRD-08", exception);
    }

    @ExceptionHandler(GradeItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(GradeItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ERR-GRD-04", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? error.getField() + " 不合法" : error.getDefaultMessage())
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("请求参数不合法");
        return badRequest("ERR-GRD-03", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return badRequest("400", "请求参数不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("400", "参数错误：" + exception.getName() + " 不合法");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public void handleResponseStatus(ResponseStatusException exception) throws ResponseStatusException {
        throw exception;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("500", "系统错误，请联系管理员"));
    }

    private ResponseEntity<ApiResponse<Void>> forbidden(String code, RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(code, exception.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String code, RuntimeException exception) {
        return badRequest(code, exception.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(ApiResponse.error(code, message));
    }
}
