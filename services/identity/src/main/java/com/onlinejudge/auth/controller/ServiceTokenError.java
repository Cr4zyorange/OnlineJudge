package com.onlinejudge.auth.controller;

public record ServiceTokenError(String code, String message, String requestId, boolean retryable) {
}
