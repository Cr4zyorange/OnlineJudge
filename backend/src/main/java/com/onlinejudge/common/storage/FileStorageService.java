package com.onlinejudge.common.storage;

import java.io.InputStream;

public interface FileStorageService {
    StoredFile store(String filename, String contentType, InputStream content);

    StoredFile load(String storageKey);

    void delete(String storageKey);
}
