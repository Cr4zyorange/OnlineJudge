package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.model.TaskState;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import com.onlinejudge.assessmentservice.worker.SandboxEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HomeworkSandboxEvaluatorTest {
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Autowired JdbcTemplate jdbc;
    @Autowired SandboxEvaluator evaluator;

    @DynamicPropertySource
    static void sandbox(DynamicPropertyRegistry registry) {
        registry.add("assessment.storage.root", () -> STORAGE_ROOT.toString());
        registry.add("assessment.sandbox.docker-api-uri", () -> "");
    }

    @AfterAll
    static void removeTemporaryStorage() throws Exception {
        try (var entries = Files.walk(STORAGE_ROOT)) {
            entries.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void homeworkExecutionFailsClosedWhenDockerSandboxIsUnavailable() throws Exception {
        long homeworkId = 315001L;
        String submissionId = "submission-sandbox-315";
        jdbc.update("DELETE FROM assessment_homework_testcase WHERE homework_id = ?", homeworkId);
        jdbc.update("DELETE FROM assessment_homework WHERE id = ?", homeworkId);
        jdbc.update("DELETE FROM assessment_submission WHERE id = ?", submissionId);
        jdbc.update("""
                INSERT INTO assessment_homework
                    (id, course_id, title, description, type, status, deadline, total_score, allow_resubmit,
                     allow_late_submit, allowed_languages, created_by, aggregate_version, created_at, updated_at)
                VALUES (?, 'course-315', 'sandbox', '', 'CODE', 'PUBLISHED', ?, 100, TRUE, FALSE,
                        'python', 'teacher-315', 2, ?, ?)
                """, homeworkId, Timestamp.from(Instant.now().plusSeconds(3600)), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        jdbc.update("""
                INSERT INTO assessment_homework_testcase
                    (homework_id, input_text, expected_output, score_weight, is_hidden, sort_order)
                VALUES (?, 'hello\\n', 'HELLO\\n', 100, TRUE, 1)
                """, homeworkId);
        var stored = new PersistentSubmissionFileStore(STORAGE_ROOT).store(submissionId, "solution.py",
                "print(input().upper())".getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO assessment_submission
                    (id, source_type, source_id, course_id, student_id, content_ref, evaluation_status, created_at)
                VALUES (?, 'HWK', ?, 'course-315', 'student-315', ?, 'PENDING', ?)
                """, submissionId, Long.toString(homeworkId), stored.storageKey(), Timestamp.from(Instant.now()));

        var outcome = evaluator.evaluate(new EvaluationTask("task-sandbox-315", submissionId, "HWK", Long.toString(homeworkId),
                "course-315", "student-315", TaskState.RUNNING, 1, "worker-315", Instant.now().plusSeconds(30), 1,
                null, "request-sandbox-315"));

        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.status()).isEqualTo("SYSTEM_ERROR");
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("assessment-homework-sandbox-");
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
