package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachment;

import java.time.LocalDateTime;

public record HomeworkAttachmentUploadResponse(
        String fileId,
        String originalFilename,
        String contentType,
        long fileSize,
        LocalDateTime expiresAt,
        String status,
        LocalDateTime uploadedAt
) {
    static HomeworkAttachmentUploadResponse from(HomeworkSubmissionAttachment attachment) {
        return new HomeworkAttachmentUploadResponse(
                attachment.publicId(),
                attachment.originalFilename(),
                attachment.contentType(),
                attachment.fileSize(),
                attachment.expiresAt(),
                attachment.status().name(),
                attachment.createdAt()
        );
    }
}
