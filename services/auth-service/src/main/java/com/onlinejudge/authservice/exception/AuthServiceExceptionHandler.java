package com.onlinejudge.authservice.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthServiceExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> api(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException exception) {
        String field = firstJsonFieldName(exception);
        String message = field == null ? "请求参数不合法" : "参数错误：" + field + " 不合法";
        return ResponseEntity.badRequest().body(ApiResponse.error("AUTH_400", message));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMediaType() {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("415", "不支持的媒体类型"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null
                        ? error.getField() + " 不合法"
                        : error.getDefaultMessage())
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.error("AUTH_400", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> mismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("AUTH_400", "参数错误：" + exception.getName() + " 不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        log.error(
                "Unhandled {} for {} {}",
                exception.getClass().getSimpleName(),
                request.getMethod(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("500", "系统错误，请联系管理员"));
    }

    private static String firstJsonFieldName(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof JsonMappingException mapping) {
            List<JsonMappingException.Reference> path = mapping.getPath();
            if (path != null && !path.isEmpty()) {
                String fieldName = path.get(path.size() - 1).getFieldName();
                if (fieldName != null && !fieldName.isBlank()) {
                    return fieldName;
                }
            }
        }
        return null;
    }
}
