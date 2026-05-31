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
        return new AuthApiException("ERR-AUTH-01", "账号或密码错误", HttpStatus.UNAUTHORIZED);
    }

    public static AuthApiException oldPasswordWrong() {
        return new AuthApiException("AUTH_401", "原密码错误", HttpStatus.UNAUTHORIZED);
    }

    public static AuthApiException disabled() {
        return new AuthApiException("ERR-AUTH-03", "账号状态异常，请联系管理员", HttpStatus.FORBIDDEN);
    }

    public static AuthApiException locked() {
        return new AuthApiException("ERR-AUTH-03", "账号状态异常，请联系管理员", HttpStatus.FORBIDDEN);
    }

    public static AuthApiException conflict(String message) {
        return new AuthApiException("AUTH_409", message, HttpStatus.CONFLICT);
    }

    public static AuthApiException notFound(String message) {
        return new AuthApiException("AUTH_404", message, HttpStatus.NOT_FOUND);
    }
}
