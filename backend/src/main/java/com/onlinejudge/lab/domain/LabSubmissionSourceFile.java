package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabSubmissionSourceFile(
        long id,
        long submissionId,
        long labId,
        long courseId,
        long uploaderId,
        String storageKey,
        String originalFilename,
        String contentType,
        long fileSize,
        LabSubmissionSourceFileStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
