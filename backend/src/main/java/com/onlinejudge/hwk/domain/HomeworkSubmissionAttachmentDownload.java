package com.onlinejudge.hwk.domain;

import org.springframework.core.io.Resource;

public record HomeworkSubmissionAttachmentDownload(
        String originalFilename,
        String contentType,
        long fileSize,
        Resource resource
) {
}
