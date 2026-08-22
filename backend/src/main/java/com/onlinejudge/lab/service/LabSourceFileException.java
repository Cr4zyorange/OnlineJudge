package com.onlinejudge.lab.service;

import com.onlinejudge.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class LabSourceFileException extends ApiException {
    private LabSourceFileException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static LabSourceFileException noFile() {
        return new LabSourceFileException("LAB-404-03", "该提交没有源文件", HttpStatus.NOT_FOUND);
    }

    public static LabSourceFileException unavailable() {
        return new LabSourceFileException("LAB-409-03", "提交源文件当前不可下载", HttpStatus.CONFLICT);
    }

    public static LabSourceFileException storageFailure() {
        return new LabSourceFileException("LAB-500-05", "提交源文件读取失败", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
