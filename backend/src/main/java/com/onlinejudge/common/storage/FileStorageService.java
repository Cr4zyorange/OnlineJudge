package com.onlinejudge.common.storage;

import java.io.InputStream;
import java.util.List;

public interface FileStorageService {
    StoredFile store(String filename, String contentType, InputStream content);

    StoredFile load(String storageKey);

    void delete(String storageKey);

    void deferDelete(String storageKey);

    List<String> pendingDeletes(int limit);

    void completeDeferredDelete(String storageKey);
}
