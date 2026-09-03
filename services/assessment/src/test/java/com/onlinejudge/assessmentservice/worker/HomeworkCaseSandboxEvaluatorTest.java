package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.model.TaskState;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/** HWK code evaluation must pass the persisted testcase input to the isolated runtime. */
@SpringBootTest
class HomeworkCaseSandboxEvaluatorTest {
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_homework_testcase WHERE homework_id = 320001");
        jdbc.update("DELETE FROM assessment_homework WHERE id = 320001");
        jdbc.update("DELETE FROM assessment_submission WHERE id = 'submission-homework-case-320'");
    }

    @AfterAll
    static void removeTemporaryStorage() throws Exception {
        try (var entries = Files.walk(STORAGE_ROOT)) {
            entries.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void codeHomeworkUsesItsPersistedInputAndAwardsTheConfiguredScore() throws Exception {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO assessment_homework
                    (id, course_id, title, description, type, status, deadline, total_score, allow_resubmit,
                     allow_late_submit, allowed_languages, created_by, aggregate_version, created_at, updated_at)
                VALUES (320001, 'course-320', 'case execution', '', 'CODE', 'PUBLISHED', ?, 100, TRUE, FALSE,
                        'python', 'teacher-320', 2, ?, ?)
                """, Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO assessment_homework_testcase
                    (homework_id, input_text, expected_output, score_weight, is_hidden, sort_order)
                VALUES (320001, '1 2', '3', 100, TRUE, 1)
                """);
        var stored = new PersistentSubmissionFileStore(STORAGE_ROOT).store("submission-homework-case-320", "solution.py",
                "left, right = map(int, input().split())\nprint(left + right)\n".getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO assessment_submission
                    (id, source_type, source_id, course_id, student_id, content_ref, evaluation_status, created_at)
                VALUES ('submission-homework-case-320', 'HWK', '320001', 'course-320', 'student-320', ?, 'PENDING', ?)
                """, stored.storageKey(), Timestamp.from(now));

        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxEvaluator evaluator = new SandboxEvaluator(jdbc, STORAGE_ROOT, sandbox);
        var outcome = evaluator.evaluate(new EvaluationTask("task-homework-case-320", "submission-homework-case-320", "HWK",
                "320001", "course-320", "student-320", TaskState.RUNNING, 1, "worker-320", now.plusSeconds(30),
                1, null, "request-homework-case-320"));

        assertThat(sandbox.input).isEqualTo("1 2");
        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.status()).isEqualTo("ACCEPTED");
        assertThat(outcome.score()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(outcome.fullScore()).isEqualByComparingTo(new BigDecimal("100"));
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("assessment-homework-cases-");
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static final class RecordingSandbox implements SandboxExecutionClient {
        private String input;

        @Override
        public DockerSandboxClient.Result evaluate(String language, byte[] source, String input, int timeLimitMs, int memoryLimitKb) {
            this.input = input;
            return "1 2".equals(input)
                    ? new DockerSandboxClient.Result("3\n", null)
                    : new DockerSandboxClient.Result("", "RUNTIME_ERROR");
        }
    }
}
