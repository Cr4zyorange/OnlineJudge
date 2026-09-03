package com.onlinejudge.common;

import com.onlinejudge.common.storage.LocalDiskFileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskFileStorageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadRejectsTraversalEvenWhenTheOutsideFileExists() throws Exception {
        Path storageRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        Files.writeString(temporaryDirectory.resolve("outside.txt"), "must not be readable");
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(storageRoot.toString());

        assertThatThrownBy(() -> storage.load("../outside.txt"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deferredDeletionQueueSurvivesStorageServiceRestartAndClearsAfterSuccess() throws Exception {
        Path storageRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(storageRoot.toString());
        StoredFile stored = storage.store(
                "answer.pdf",
                "application/pdf",
                new ByteArrayInputStream("persistent orphan".getBytes(StandardCharsets.UTF_8))
        );

        storage.deferDelete(stored.storageKey());

        LocalDiskFileStorageService restarted = new LocalDiskFileStorageService(storageRoot.toString());
        List<String> pending = restarted.pendingDeletes(10);
        assertThat(pending).containsExactly(stored.storageKey());

        restarted.delete(stored.storageKey());
        restarted.completeDeferredDelete(stored.storageKey());

        List<String> remaining = restarted.pendingDeletes(10);
        assertThat(remaining).isEmpty();
        assertThatThrownBy(() -> restarted.load(stored.storageKey()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedStreamCopyRemovesTheKnownPartialObject() throws Exception {
        Path storageRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(storageRoot.toString());

        assertThatThrownBy(() -> storage.store("partial.pdf", "application/pdf", failingStream()))
                .isInstanceOf(IllegalStateException.class);

        try (var paths = Files.list(storageRoot)) {
            assertThat(paths.toList()).isEmpty();
        }
        assertThat(storage.pendingDeletes(10)).isEmpty();
    }

    @Test
    void failedStreamCopyJournalsThePartialObjectWhenImmediateDeleteFails() throws Exception {
        Path storageRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        LocalDiskFileStorageService storage = new DeleteFailingLocalDiskFileStorageService(storageRoot.toString());

        assertThatThrownBy(() -> storage.store("partial.pdf", "application/pdf", failingStream()))
                .isInstanceOf(IllegalStateException.class);

        List<String> pending = storage.pendingDeletes(10);
        assertThat(pending).hasSize(1);
        assertThat(Files.exists(storageRoot.resolve(pending.getFirst()))).isTrue();

        LocalDiskFileStorageService recovered = new LocalDiskFileStorageService(storageRoot.toString());
        recovered.delete(pending.getFirst());
        recovered.completeDeferredDelete(pending.getFirst());
        assertThat(recovered.pendingDeletes(10)).isEmpty();
    }

    @Test
    void malformedMarkersDoNotStarveLaterValidDeferredDeletes() throws Exception {
        Path storageRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(storageRoot.toString());
        StoredFile first = storage.store(
                "first.pdf",
                "application/pdf",
                new ByteArrayInputStream("first deferred object".getBytes(StandardCharsets.UTF_8))
        );
        StoredFile second = storage.store(
                "second.pdf",
                "application/pdf",
                new ByteArrayInputStream("second deferred object".getBytes(StandardCharsets.UTF_8))
        );
        storage.deferDelete(first.storageKey());
        storage.deferDelete(second.storageKey());

        Path pendingDirectory = storageRoot.resolve(".pending-deletes");
        Path firstEnumeratedMarker;
        try (var markers = Files.list(pendingDirectory)) {
            firstEnumeratedMarker = markers.findFirst().orElseThrow();
        }
        String corruptedStorageKey = Files.readString(firstEnumeratedMarker, StandardCharsets.UTF_8);
        String expectedStorageKey = corruptedStorageKey.equals(first.storageKey())
                ? second.storageKey()
                : first.storageKey();
        Files.writeString(firstEnumeratedMarker, "malformed marker", StandardCharsets.UTF_8);

        assertThat(storage.pendingDeletes(1)).containsExactly(expectedStorageKey);
    }

    private InputStream failingStream() {
        return new InputStream() {
            private final byte[] bytes = "partial bytes".getBytes(StandardCharsets.UTF_8);
            private int index;

            @Override
            public int read() throws IOException {
                if (index < bytes.length) {
                    return bytes[index++] & 0xff;
                }
                throw new IOException("source stream failed");
            }
        };
    }

    private static final class DeleteFailingLocalDiskFileStorageService extends LocalDiskFileStorageService {
        private DeleteFailingLocalDiskFileStorageService(String rootDirectory) {
            super(rootDirectory);
        }

        @Override
        public void delete(String storageKey) {
            throw new IllegalStateException("storage delete unavailable");
        }
    }
}
