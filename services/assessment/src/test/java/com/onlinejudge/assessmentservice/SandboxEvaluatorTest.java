package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import com.onlinejudge.assessmentservice.worker.SandboxEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxEvaluatorTest {
    @TempDir Path files;

    @Test
    void evaluatorRunsConfiguredExternalRunnerAgainstPersistedSubmissionInsteadOfReturningSyntheticSuccess() throws Exception {
        var store = new PersistentSubmissionFileStore(files);
        var stored = store.store("submission-7", "answer.txt", "answer".getBytes());
        var evaluator = new SandboxEvaluator(files, "/usr/bin/true", Duration.ofSeconds(2));

        assertThat(evaluator.evaluate(stored.storageKey()).successful()).isTrue();
        assertThat(new SandboxEvaluator(files, "/usr/bin/false", Duration.ofSeconds(2)).evaluate(stored.storageKey()).successful()).isFalse();
    }
}
