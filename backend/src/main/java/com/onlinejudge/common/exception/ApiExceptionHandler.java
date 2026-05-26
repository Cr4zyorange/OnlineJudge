package com.onlinejudge.common.exception;

import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.service.GradeItemPermissionException;
import com.onlinejudge.grd.service.GradeItemNotFoundException;
import com.onlinejudge.grd.service.InvalidGradeRuleException;
import com.onlinejudge.lab.service.LabNotFoundException;
import com.onlinejudge.lab.service.LabPermissionException;
import com.onlinejudge.lab.service.LabStateException;
import com.onlinejudge.lab.service.LabValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(GradeItemPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermission(GradeItemPermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ERR-GRD-01", exception.getMessage()));
    }

    @ExceptionHandler(InvalidGradeRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRule(InvalidGradeRuleException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR-GRD-03", exception.getMessage()));
    }

    @ExceptionHandler(GradeItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(GradeItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ERR-GRD-04", exception.getMessage()));
    }

    @ExceptionHandler(LabPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleLabPermission(LabPermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("LAB-403-01", exception.getMessage()));
    }

    @ExceptionHandler(LabValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLabValidation(LabValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("LAB-400-01", exception.getMessage()));
    }

    @ExceptionHandler(LabNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLabNotFound(LabNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("LAB-404-01", exception.getMessage()));
    }

    @ExceptionHandler(LabStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleLabState(LabStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("LAB-409-01", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? error.getField() + " 不合法" : error.getDefaultMessage())
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("请求参数不合法");
        String code = exception.getBindingResult().getObjectName().toLowerCase().contains("lab")
                ? "LAB-400-01"
                : "ERR-GRD-03";
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(code, message));
    }
}
