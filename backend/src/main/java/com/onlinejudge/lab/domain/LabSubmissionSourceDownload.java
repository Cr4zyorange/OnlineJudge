package com.onlinejudge.lab.domain;

import org.springframework.core.io.Resource;

public record LabSubmissionSourceDownload(
        String originalFilename,
        String contentType,
        long fileSize,
        Resource resource
) {
}
