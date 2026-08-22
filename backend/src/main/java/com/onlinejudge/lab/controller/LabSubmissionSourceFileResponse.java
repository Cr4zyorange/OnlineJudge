package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabSubmissionSourceFileView;

public record LabSubmissionSourceFileResponse(
        String originalFilename,
        String contentType,
        long fileSize,
        boolean downloadAvailable
) {
    public static LabSubmissionSourceFileResponse from(LabSubmissionSourceFileView sourceFile) {
        return new LabSubmissionSourceFileResponse(
                sourceFile.originalFilename(),
                sourceFile.contentType(),
                sourceFile.fileSize(),
                sourceFile.downloadAvailable()
        );
    }
}
