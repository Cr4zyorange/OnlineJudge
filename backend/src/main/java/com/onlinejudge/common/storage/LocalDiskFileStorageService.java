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
        String storageKey = UUID.randomUUID() + safeExtension(safeFilename);
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
        Path targetPath = rootDirectory.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootDirectory)) {
            throw new IllegalStateException("文件路径不合法");
        }
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new IllegalStateException("文件删除失败", exception);
        }
    }

    private String sanitizeFilename(String filename) {
        String value = Objects.requireNonNullElse(filename, "submission.bin").trim();
        if (value.isEmpty()) {
            return "submission.bin";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), 255));
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            sanitized.append(current == '/' || current == '\\' || Character.isISOControl(current) ? '_' : current);
        }
        if (sanitized.length() <= 255) {
            return sanitized.toString();
        }

        int extensionIndex = sanitized.lastIndexOf(".");
        String extension = extensionIndex > 0 && sanitized.length() - extensionIndex <= 20
                ? sanitized.substring(extensionIndex)
                : "";
        return sanitized.substring(0, 255 - extension.length()) + extension;
    }

    private String safeExtension(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex <= 0 || filename.length() - extensionIndex > 20) {
            return "";
        }
        return filename.substring(extensionIndex).toLowerCase(java.util.Locale.ROOT);
    }
}
