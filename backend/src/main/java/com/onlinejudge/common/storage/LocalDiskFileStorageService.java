package com.onlinejudge.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Primary
public class LocalDiskFileStorageService implements FileStorageService {
    private static final String PENDING_DELETE_DIRECTORY = ".pending-deletes";
    private static final String PENDING_DELETE_SUFFIX = ".pending";
    private static final long MAX_PENDING_MARKER_BYTES = 4096L;

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
        } catch (IOException | RuntimeException exception) {
            cleanupFailedStore(storageKey, exception);
            throw new IllegalStateException("文件存储失败", exception);
        }
    }

    @Override
    public StoredFile load(String storageKey) {
        Path targetPath = resolveStoragePath(storageKey);
        if (!Files.exists(targetPath)) {
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
        Path targetPath = resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new IllegalStateException("文件删除失败", exception);
        }
    }

    @Override
    public void deferDelete(String storageKey) {
        resolveStoragePath(storageKey);
        Path pendingDirectory = pendingDeleteDirectory();
        Path marker = pendingDirectory.resolve(pendingMarkerName(storageKey));
        Path temporaryMarker = null;
        try {
            Files.createDirectories(pendingDirectory);
            temporaryMarker = Files.createTempFile(pendingDirectory, ".pending-", ".tmp");
            Files.writeString(temporaryMarker, storageKey, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporaryMarker,
                        marker,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryMarker, marker, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("待删除文件记录失败", exception);
        } finally {
            if (temporaryMarker != null) {
                try {
                    Files.deleteIfExists(temporaryMarker);
                } catch (IOException ignored) {
                    // A successfully moved marker no longer has a temporary path to remove.
                }
            }
        }
    }

    @Override
    public List<String> pendingDeletes(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Path pendingDirectory = pendingDeleteDirectory();
        if (!Files.isDirectory(pendingDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(pendingDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(PENDING_DELETE_SUFFIX))
                    .map(this::readPendingStorageKey)
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("待删除文件读取失败", exception);
        }
    }

    @Override
    public void completeDeferredDelete(String storageKey) {
        resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(pendingDeleteDirectory().resolve(pendingMarkerName(storageKey)));
        } catch (IOException exception) {
            throw new IllegalStateException("待删除文件确认失败", exception);
        }
    }

    private Path resolveStoragePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalStateException("文件路径不合法");
        }
        Path targetPath = rootDirectory.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootDirectory) || targetPath.equals(rootDirectory)) {
            throw new IllegalStateException("文件路径不合法");
        }
        return targetPath;
    }

    private void cleanupFailedStore(String storageKey, Exception storeFailure) {
        try {
            delete(storageKey);
        } catch (RuntimeException deleteFailure) {
            try {
                deferDelete(storageKey);
            } catch (RuntimeException journalFailure) {
                storeFailure.addSuppressed(deleteFailure);
                storeFailure.addSuppressed(journalFailure);
            }
        }
    }

    private Path pendingDeleteDirectory() {
        return rootDirectory.resolve(PENDING_DELETE_DIRECTORY);
    }

    private String readPendingStorageKey(Path marker) {
        try {
            if (!marker.normalize().startsWith(pendingDeleteDirectory())
                    || !Files.isRegularFile(marker)
                    || Files.size(marker) > MAX_PENDING_MARKER_BYTES) {
                return null;
            }
            String storageKey = Files.readString(marker, StandardCharsets.UTF_8);
            resolveStoragePath(storageKey);
            return marker.getFileName().toString().equals(pendingMarkerName(storageKey)) ? storageKey : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private String pendingMarkerName(String storageKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(storageKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest) + PENDING_DELETE_SUFFIX;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("待删除文件记录失败", exception);
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
