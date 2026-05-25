package com.onlinejudge.grd.service;

public class InvalidGradeRuleException extends RuntimeException {
    public InvalidGradeRuleException(String message) {
        super(message);
    }
}
