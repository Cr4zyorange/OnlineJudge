package com.onlinejudge.crs.service;

import org.springframework.core.io.Resource;

public record ResourceDownload(
        Resource content,
        String filename,
        String contentType,
        long fileSize
) {
}
