package com.onlinejudge.auth.exception;

import com.onlinejudge.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AuthApiException extends ApiException {
    public AuthApiException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static AuthApiException badRequest(String message) {
        return new AuthApiException("AUTH_400", message, HttpStatus.BAD_REQUEST);
    }

    public static AuthApiException loginFailed() {
        return new AuthApiException("AUTH_401", "账号或密码错误", HttpStatus.UNAUTHORIZED);
    }

    public static AuthApiException disabled() {
        return new AuthApiException("AUTH_403", "账号状态异常，请联系管理员", HttpStatus.FORBIDDEN);
    }

    public static AuthApiException locked() {
        return new AuthApiException("AUTH_423", "账号已被锁定，请稍后重试", HttpStatus.LOCKED);
    }

    public static AuthApiException conflict(String message) {
        return new AuthApiException("AUTH_409", message, HttpStatus.CONFLICT);
    }
}
