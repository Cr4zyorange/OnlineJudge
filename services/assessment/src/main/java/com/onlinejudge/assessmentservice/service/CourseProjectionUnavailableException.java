package com.onlinejudge.assessmentservice.service;

/** The local Course membership projection is not safe to use for a write. */
public class CourseProjectionUnavailableException extends RuntimeException {
    public CourseProjectionUnavailableException(String message) { super(message); }
}
