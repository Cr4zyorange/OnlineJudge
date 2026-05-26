package com.onlinejudge.common.security;

import com.onlinejudge.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AuthenticationRequiredException extends ApiException {
    public AuthenticationRequiredException(String message) {
        super("ERR-AUTH-01", message, HttpStatus.UNAUTHORIZED);
    }
}
