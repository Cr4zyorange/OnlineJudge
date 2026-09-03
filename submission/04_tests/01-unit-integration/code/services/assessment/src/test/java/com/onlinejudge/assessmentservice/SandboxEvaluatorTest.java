package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import com.onlinejudge.assessmentservice.worker.SandboxEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxEvaluatorTest {
    @TempDir Path files;

    @Test
    void evaluatorDoesNotFallBackToAHostProcessWhenNoDockerSandboxIsConfigured() throws Exception {
        var store = new PersistentSubmissionFileStore(files);
        var stored = store.store("submission-7", "answer.txt", "answer".getBytes());
        var evaluator = new SandboxEvaluator(files);

        assertThat(evaluator.evaluate(stored.storageKey()).successful()).isFalse();
        assertThat(evaluator.evaluate(stored.storageKey()).status()).isEqualTo("SYSTEM_ERROR");
    }
}
