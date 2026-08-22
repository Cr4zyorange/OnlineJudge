package com.onlinejudge.lab.domain;

public record LabSubmissionSourceFileView(
        String originalFilename,
        String contentType,
        long fileSize,
        boolean downloadAvailable
) {
    public LabSubmissionSourceFileView withDownloadAvailable(boolean available) {
        return new LabSubmissionSourceFileView(originalFilename, contentType, fileSize, available);
    }
}
