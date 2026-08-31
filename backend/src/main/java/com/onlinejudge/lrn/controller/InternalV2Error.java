package com.onlinejudge.lrn.controller;

/** course.openapi.json Error body: {code, message, requestId, retryable}. */
public record InternalV2Error(String code, String message, String requestId, boolean retryable) {
}
