package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import com.onlinejudge.assessmentservice.worker.SandboxEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxEvaluatorTest {
    @TempDir Path files;

    @Test
    void evaluatorRunsConfiguredExternalRunnerAgainstPersistedSubmissionInsteadOfReturningSyntheticSuccess() throws Exception {
        var store = new PersistentSubmissionFileStore(files);
        var stored = store.store("submission-7", "answer.txt", "answer".getBytes());
        String java = ProcessHandle.current().info().command().orElseThrow();
        var evaluator = new SandboxEvaluator(files,
                List.of(java, "-cp", System.getProperty("java.class.path"), SuccessRunner.class.getName()),
                Duration.ofSeconds(15));

        assertThat(evaluator.evaluate(stored.storageKey()).successful()).isTrue();
        assertThat(new SandboxEvaluator(files,
                List.of(java, "-cp", System.getProperty("java.class.path"), FailureRunner.class.getName()),
                Duration.ofSeconds(15)).evaluate(stored.storageKey()).successful()).isFalse();
    }

    public static final class SuccessRunner { public static void main(String[] ignored) { } }
    public static final class FailureRunner { public static void main(String[] ignored) { System.exit(7); } }
}
