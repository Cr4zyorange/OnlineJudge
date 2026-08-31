package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.model.TaskState;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import com.onlinejudge.assessmentservice.worker.SandboxEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HomeworkSandboxEvaluatorTest {
    @Autowired JdbcTemplate jdbc;
    @TempDir Path storageRoot;

    @Test
    void persistedHomeworkTestcasesDetermineTheWorkerScore() throws Exception {
        long homeworkId = 315001L;
        String submissionId = "submission-sandbox-315";
        jdbc.update("DELETE FROM assessment_homework_testcase WHERE homework_id = ?", homeworkId);
        jdbc.update("DELETE FROM assessment_submission WHERE id = ?", submissionId);
        jdbc.update("""
                INSERT INTO assessment_homework_testcase
                    (homework_id, input_text, expected_output, score_weight, is_hidden, sort_order)
                VALUES (?, 'hello\n', 'HELLO\n', 100, TRUE, 1)
                """, homeworkId);
        var stored = new PersistentSubmissionFileStore(storageRoot).store(submissionId, "solution.txt", "ignored by test runner".getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO assessment_submission
                    (id, source_type, source_id, course_id, student_id, content_ref, evaluation_status, created_at)
                VALUES (?, 'HWK', ?, 'course-315', 'student-315', ?, 'PENDING', ?)
                """, submissionId, Long.toString(homeworkId), stored.storageKey(), Timestamp.from(Instant.now()));

        String java = ProcessHandle.current().info().command().orElseThrow();
        List<String> command = List.of(java, "-cp", System.getProperty("java.class.path"), UppercaseRunner.class.getName());
        SandboxEvaluator evaluator = new SandboxEvaluator(jdbc, storageRoot, command, Duration.ofSeconds(15));
        EvaluationTask task = new EvaluationTask("task-sandbox-315", submissionId, "HWK", Long.toString(homeworkId),
                "course-315", "student-315", TaskState.RUNNING, 1, "worker-315", Instant.now().plusSeconds(30), 1, null);

        var outcome = evaluator.evaluate(task);

        assertThat(outcome.status()).isEqualTo("ACCEPTED");
        assertThat(outcome.score()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(outcome.fullScore()).isEqualByComparingTo(new BigDecimal("100"));
    }

    public static final class UppercaseRunner {
        public static void main(String[] ignored) throws Exception {
            String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            System.out.print(input.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
