package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;

public record HomeworkSubmissionAttachment(
        long id,
        String publicId,
        Long submissionId,
        long homeworkId,
        long courseId,
        long uploaderId,
        String storageKey,
        String originalFilename,
        String contentType,
        long fileSize,
        HomeworkSubmissionAttachmentStatus status,
        Integer activeSlot,
        LocalDateTime expiresAt,
        LocalDateTime boundAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
