package com.onlinejudge.hwk.service;

import com.onlinejudge.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class HomeworkApiException extends ApiException {
    public HomeworkApiException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
