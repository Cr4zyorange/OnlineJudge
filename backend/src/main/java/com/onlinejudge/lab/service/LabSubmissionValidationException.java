package com.onlinejudge.lab.service;

public class LabSubmissionValidationException extends RuntimeException {
    private final String code;

    public LabSubmissionValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
