package com.onlinejudge.authservice.exception;

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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
    public ResponseEntity<ApiResponse<Void>> unreadable() {
        return ResponseEntity.badRequest().body(ApiResponse.error("AUTH_400", "请求参数不合法"));
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
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("500", "系统错误，请联系管理员"));
    }
}
