package com.onlinejudge.common.security;

import com.onlinejudge.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AccessDeniedException extends ApiException {
    public AccessDeniedException(String message) {
        super("ERR-AUTH-03", message, HttpStatus.FORBIDDEN);
    }
}
