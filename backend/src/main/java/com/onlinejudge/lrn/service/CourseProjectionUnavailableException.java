package com.onlinejudge.lrn.service;

/** The receiver scope cannot be resolved until Learning has its own Course projection. */
public class CourseProjectionUnavailableException extends RuntimeException {
    public CourseProjectionUnavailableException(String message) {
        super(message);
    }
}
