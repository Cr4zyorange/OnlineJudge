package com.onlinejudge.common.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long size,
        String url
) {
}
