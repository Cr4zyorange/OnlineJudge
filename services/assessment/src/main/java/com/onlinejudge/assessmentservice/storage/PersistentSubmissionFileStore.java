package com.onlinejudge.assessmentservice.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** A volume/object-store boundary: only generated keys are persisted, never a client path. */
public class PersistentSubmissionFileStore {
    private final Path root;
    public PersistentSubmissionFileStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public StoredFile store(String submissionId, String originalFilename, byte[] bytes) throws IOException {
        if (submissionId == null || !submissionId.matches("[A-Za-z0-9-]+")) throw new IllegalArgumentException("submission id is invalid");
        String safeName = originalFilename == null ? "submission.bin" : originalFilename.replaceAll("[\\r\\n/\\\\]", "_");
        String key = "submissions/" + submissionId + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("storage key escapes root");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new StoredFile(key, safeName, bytes.length);
    }
    /** Best-effort compensation for a database transaction that did not commit. */
    public void delete(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank()) return;
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("storage key escapes root");
        Files.deleteIfExists(target);
    }
    public record StoredFile(String storageKey, String originalFilename, long size) { }
}
