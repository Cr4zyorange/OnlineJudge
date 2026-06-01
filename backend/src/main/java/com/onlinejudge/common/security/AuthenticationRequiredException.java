package com.onlinejudge.common.security;

import com.onlinejudge.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AuthenticationRequiredException extends ApiException {
    public AuthenticationRequiredException(String message) {
        super("ERR-AUTH-04", "登录已失效，请重新登录", HttpStatus.UNAUTHORIZED);
    }
}
