package com.onlinejudge.common.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long size,
        String url,
        org.springframework.core.io.Resource resource
) {
    public StoredFile(String storageKey, String originalFilename, String contentType, long size, String url) {
        this(storageKey, originalFilename, contentType, size, url, null);
    }
}
