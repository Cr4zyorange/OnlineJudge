package com.onlinejudge.hwk.domain;

public record HomeworkSubmissionAttachmentView(
        String originalFilename,
        String contentType,
        long fileSize,
        boolean downloadAvailable
) {
}
