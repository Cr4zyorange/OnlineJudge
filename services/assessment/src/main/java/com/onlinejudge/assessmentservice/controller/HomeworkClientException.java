package com.onlinejudge.assessmentservice.controller;

import org.springframework.http.HttpStatusCode;

/** A documented HWK error whose stable code cannot be inferred from HTTP status alone. */
final class HomeworkClientException extends RuntimeException {
    private final HttpStatusCode status;
    private final String code;

    HomeworkClientException(HttpStatusCode status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatusCode status() { return status; }
    String code() { return code; }
}
