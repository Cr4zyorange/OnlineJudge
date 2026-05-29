package com.onlinejudge.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Service
@Primary
public class LocalDiskFileStorageService implements FileStorageService {
    private final Path rootDirectory;

    public LocalDiskFileStorageService(@Value("${onlinejudge.storage.local-root:./data/uploads}") String rootDirectory) {
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String filename, String contentType, InputStream content) {
        String safeFilename = sanitizeFilename(filename);
        String storageKey = UUID.randomUUID() + "-" + safeFilename;
        Path targetPath = rootDirectory.resolve(storageKey).normalize();

        try {
            Files.createDirectories(rootDirectory);
            Files.copy(content, targetPath, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(targetPath);
            return new StoredFile(
                    storageKey,
                    safeFilename,
                    contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                    size,
                    targetPath.toUri().toString()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("文件存储失败", exception);
        }
    }

    @Override
    public StoredFile load(String storageKey) {
        Path targetPath = rootDirectory.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootDirectory) || !Files.exists(targetPath)) {
            throw new IllegalStateException("文件不存在");
        }
        try {
            return new StoredFile(
                    storageKey,
                    targetPath.getFileName().toString(),
                    "application/octet-stream",
                    Files.size(targetPath),
                    targetPath.toUri().toString(),
                    new FileSystemResource(targetPath)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("文件读取失败", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(rootDirectory.resolve(storageKey).normalize());
        } catch (IOException exception) {
            throw new IllegalStateException("文件删除失败", exception);
        }
    }

    private String sanitizeFilename(String filename) {
        String value = Objects.requireNonNullElse(filename, "submission.bin").trim();
        if (value.isEmpty()) {
            return "submission.bin";
        }
        return value.replace("\\", "_").replace("/", "_");
    }
}
