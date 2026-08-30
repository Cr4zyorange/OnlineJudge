package com.onlinejudge.assessmentservice.controller;

/** Contract-shaped 400 for malformed internal rebuild reads. */
final class SourceGradeRequestException extends RuntimeException {
    SourceGradeRequestException(String message) { super(message); }
}
