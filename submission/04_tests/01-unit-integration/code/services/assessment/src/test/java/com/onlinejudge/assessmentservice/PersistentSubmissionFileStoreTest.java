package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import static org.assertj.core.api.Assertions.assertThat;

class PersistentSubmissionFileStoreTest {
    @Test
    void storesSubmissionBytesUnderConfiguredPersistentRootNotAnInstanceTempPath() throws Exception {
        var root = Files.createTempDirectory("assessment-volume-");
        var store = new PersistentSubmissionFileStore(root);
        var stored = store.store("submission-1", "answer.py", "print('ok')".getBytes());
        assertThat(stored.storageKey()).startsWith("submissions/submission-1/");
        assertThat(Files.readString(root.resolve(stored.storageKey()))).isEqualTo("print('ok')");
    }
}
