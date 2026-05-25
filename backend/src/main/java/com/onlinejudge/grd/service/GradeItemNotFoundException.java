package com.onlinejudge.grd.service;

public class GradeItemNotFoundException extends RuntimeException {
    public GradeItemNotFoundException(String message) {
        super(message);
    }
}
