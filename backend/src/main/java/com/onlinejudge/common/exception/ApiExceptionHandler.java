package com.onlinejudge.common.exception;

import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.service.GradeAdjustmentException;
import com.onlinejudge.grd.service.GradeItemPermissionException;
import com.onlinejudge.grd.service.GradeItemNotFoundException;
import com.onlinejudge.grd.service.GradePublishException;
import com.onlinejudge.grd.service.GradeReviewDuplicateException;
import com.onlinejudge.grd.service.GradeReviewPermissionException;
import com.onlinejudge.grd.service.GradeReviewValidationException;
import com.onlinejudge.grd.service.InvalidGradeRuleException;
import com.onlinejudge.grd.service.StudentGradeAccessException;
import com.onlinejudge.lab.service.LabNotFoundException;
import com.onlinejudge.lab.service.LabPermissionException;
import com.onlinejudge.lab.service.LabStateException;
import com.onlinejudge.lab.service.LabSubmissionValidationException;
import com.onlinejudge.lab.service.LabValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.regex.Pattern;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Pattern HOMEWORK_ATTACHMENT_UPLOAD_PATH = Pattern.compile(
            "^/api/v1/homeworks/[0-9]+/attachments/?$"
    );

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequestBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("AUTH_400", "请求参数不合法"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        if (!HOMEWORK_ATTACHMENT_UPLOAD_PATH.matcher(request.getRequestURI()).matches()) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error("413", "upload exceeds the configured limit"));
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("HWK_4131", "attachment exceeds the upload limit"));
    }

    @ExceptionHandler(GradeItemPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermission(GradeItemPermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ERR-GRD-01", exception.getMessage()));
    }

    @ExceptionHandler(StudentGradeAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleStudentGradeAccess(StudentGradeAccessException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ERR-GRD-02", exception.getMessage()));
    }

    @ExceptionHandler(InvalidGradeRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRule(InvalidGradeRuleException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR-GRD-03", exception.getMessage()));
    }

    @ExceptionHandler(GradeAdjustmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleGradeAdjustment(GradeAdjustmentException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR-GRD-06", exception.getMessage()));
    }

    @ExceptionHandler(GradePublishException.class)
    public ResponseEntity<ApiResponse<Void>> handleGradePublish(GradePublishException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR-GRD-04", exception.getMessage()));
    }

    @ExceptionHandler(GradeReviewDuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleGradeReviewDuplicate(GradeReviewDuplicateException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR-GRD-08", exception.getMessage()));
    }

    @ExceptionHandler(GradeReviewPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleGradeReviewPermission(GradeReviewPermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ERR-GRD-09", exception.getMessage()));
    }

    @ExceptionHandler(GradeReviewValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleGradeReviewValidation(GradeReviewValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR-GRD-04", exception.getMessage()));
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

    @ExceptionHandler(LabSubmissionValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLabSubmissionValidation(LabSubmissionValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
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
        String objectName = exception.getBindingResult().getObjectName().toLowerCase();
        if (objectName.contains("course") || objectName.contains("chapter")
                || objectName.contains("resource") || objectName.contains("announcement")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("CRS_400", "参数错误：" + message));
        }
        String code = objectName.contains("lab") ? "LAB-400-01" : "ERR-GRD-03";
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(code, message));
    }
}
