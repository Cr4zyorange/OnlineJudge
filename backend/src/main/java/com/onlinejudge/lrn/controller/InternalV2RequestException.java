package com.onlinejudge.lrn.controller;

import org.springframework.http.HttpStatus;

/** Contract-shaped v2 internal error used by the Learning receiving boundary. */
public class InternalV2RequestException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final boolean retryable;

    public InternalV2RequestException(String code, String message, HttpStatus status, boolean retryable) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }
}
