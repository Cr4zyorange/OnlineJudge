package com.onlinejudge.courseservice.service;

import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Local, non-root writable storage for Course file resources. */
@Component
public class CourseFileStorage {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "txt", "md", "zip", "rar", "png", "jpg", "jpeg", "gif", "mp4");

    private final Path root;

    public CourseFileStorage(@Value("${course.storage.root:${java.io.tmpdir}/onlinejudge-course}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("resource file is required");
        if (file.getSize() > MAX_BYTES) throw invalid("resource file exceeds 50MB");
        String original = safeFilename(file.getOriginalFilename());
        String extension = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw invalid("resource file type is not allowed");
        String key = UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(root);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(key, original, file.getContentType() == null ? "application/octet-stream" : file.getContentType(), file.getSize());
        } catch (IOException exception) {
            throw new CourseException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_STORAGE_FAILED", "resource file could not be stored", true);
        }
    }

    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException exception) {
            throw new CourseException(HttpStatus.NOT_FOUND, "RESOURCE_CONTENT_NOT_FOUND", "resource content does not exist", false);
        }
    }

    public void deleteQuietly(String key) {
        try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) { }
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || key.contains("/")) throw invalid("resource storage key is invalid");
        return resolved;
    }

    private String safeFilename(String value) {
        String filename = value == null ? "" : Path.of(value).getFileName().toString();
        if (filename.isBlank() || filename.length() > 255) throw invalid("resource filename is invalid");
        return filename;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 1 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private CourseException invalid(String message) { return new CourseException(HttpStatus.BAD_REQUEST, "RESOURCE_INVALID", message, false); }

    public record StoredFile(String storageKey, String originalFilename, String contentType, long size) { }
}
