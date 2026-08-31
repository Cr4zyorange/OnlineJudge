package com.onlinejudge.courseservice.web;

public record ErrorResponse(String code, String message, String requestId, boolean retryable) { }
