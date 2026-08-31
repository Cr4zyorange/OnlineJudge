package com.onlinejudge.courseservice.service;

import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Local, non-root writable storage for Course file resources. */
@Component
public class CourseFileStorage {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final int SNIFF_LENGTH = 4096;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "txt", "md", "zip", "rar", "png", "jpg", "jpeg", "gif", "mp4");
    private static final Map<String, String> TRUSTED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("mp4", "video/mp4"));

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
        } catch (IOException exception) {
            throw new CourseException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_STORAGE_FAILED", "resource file could not be stored", true);
        }
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] header = in.readNBytes(SNIFF_LENGTH);
            validateContent(extension, header);
            out.write(header);
            in.transferTo(out);
            return new StoredFile(key, original, trustedContentType(extension), file.getSize());
        } catch (CourseException rejected) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw rejected;
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

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new CourseException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_STORAGE_FAILED",
                    "resource file could not be deleted from storage", true);
        }
    }

    /**
     * Best-effort compensation only for the upload-failure path: the primary
     * error is already surfaced to the caller and no journal row exists to
     * keep PENDING, so a cleanup miss must not replace that error.
     */
    public void deleteQuietly(String key) {
        try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) { }
    }

    /**
     * The persisted MIME type and the download response are derived from the
     * accepted extension, never from a multipart header supplied by the user.
     * The octet-stream fallback keeps existing malformed legacy rows safe.
     */
    public String trustedContentTypeForFilename(String filename) {
        return trustedContentType(extension(filename == null ? "" : filename));
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

    private String trustedContentType(String extension) {
        return TRUSTED_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    /**
     * CRS-SC-03 controlled rejection: reject executable payloads regardless of
     * the claimed extension, and reject content that does not match the allowed
     * extension's signature so renamed or fabricated files cannot be stored.
     */
    private void validateContent(String extension, byte[] header) {
        if (looksExecutable(header)) throw invalid("resource file content looks like an executable");
        if (!contentMatchesExtension(extension, header)) throw invalid("resource file content does not match its type");
    }

    private boolean looksExecutable(byte[] header) {
        if (header.length >= 2) {
            if (header[0] == 'M' && header[1] == 'Z') return true;
            if (header[0] == '#' && header[1] == '!') return true;
        }
        if (header.length >= 4 && (header[0] & 0xFF) == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') return true;
        if (header.length >= 4) {
            int magic = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16) | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            return switch (magic) {
                case 0xFEEDFACE, 0xCEFAEDFE, 0xFEEDFACF, 0xCFFAEDFE, 0xCAFEBABE, 0xBEBAFECA -> true;
                default -> false;
            };
        }
        return false;
    }

    private boolean contentMatchesExtension(String extension, byte[] header) {
        return switch (extension) {
            case "pdf" -> startsWith(header, "%PDF");
            case "doc", "ppt", "xls" -> startsWith(header,
                    new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            case "docx", "pptx", "xlsx", "zip" -> startsWith(header, "PK");
            case "rar" -> header.length >= 6 && header[0] == 'R' && header[1] == 'a' && header[2] == 'r' && header[3] == '!'
                    && header[4] == 0x1A && header[5] == 0x07;
            case "png" -> startsWith(header,
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "jpg", "jpeg" -> header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "gif" -> startsWith(header, "GIF8");
            case "mp4" -> header.length >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            case "txt", "md" -> true;
            default -> false;
        };
    }

    private boolean startsWith(byte[] header, String prefix) {
        return startsWith(header, prefix.getBytes(StandardCharsets.UTF_8));
    }

    private boolean startsWith(byte[] header, byte[] magic) {
        if (header.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) return false;
        }
        return true;
    }

    private CourseException invalid(String message) { return new CourseException(HttpStatus.BAD_REQUEST, "RESOURCE_INVALID", message, false); }

    public record StoredFile(String storageKey, String originalFilename, String contentType, long size) { }
}
