package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HomeworkWorkerFencingTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EvaluationTaskRepository tasks;
    @Autowired WorkerCompletionService completion;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_homework_submission");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_homework_testcase");
        jdbc.update("DELETE FROM assessment_homework");
    }

    @Test
    void expiredWorkerCannotOverwriteHomeworkResultOrCreateDuplicateFacts() {
        Instant started = Instant.parse("2026-08-31T01:00:00Z");
        long homeworkId = 315900L;
        String submissionId = "submission-fencing-315";
        String taskId = "task-fencing-315";
        jdbc.update("""
                INSERT INTO assessment_homework
                    (id, course_id, title, description, type, status, deadline, total_score, allow_resubmit,
                     allow_late_submit, allowed_languages, created_by, aggregate_version, created_at, updated_at)
                VALUES (?, 'course-315', 'fencing', '', 'CODE', 'PUBLISHED', ?, 100, TRUE, FALSE,
                        'python', 'teacher-315', 2, ?, ?)
                """, homeworkId, Timestamp.from(started.plusSeconds(3600)), Timestamp.from(started), Timestamp.from(started));
        jdbc.update("""
                INSERT INTO assessment_submission
                    (id, source_type, source_id, course_id, student_id, content_ref, evaluation_status, created_at)
                VALUES (?, 'HWK', ?, 'course-315', 'student-315', 'submissions/fencing/source.py', 'PENDING', ?)
                """, submissionId, Long.toString(homeworkId), Timestamp.from(started));
        jdbc.update("""
                INSERT INTO assessment_homework_submission
                    (submission_id, homework_id, student_id, submission_version, language, submit_status,
                     evaluation_status, is_final, submitted_at)
                VALUES (?, ?, 'student-315', 1, 'python', 'SUBMITTED', 'PENDING', TRUE, ?)
                """, submissionId, homeworkId, Timestamp.from(started));
        tasks.insert(taskId, submissionId, "HWK", Long.toString(homeworkId), "course-315", "student-315", started);

        var expired = tasks.claimNext("worker-a", started, Duration.ofSeconds(10)).orElseThrow();
        var replacement = tasks.claimNext("worker-b", started.plusSeconds(11), Duration.ofSeconds(10)).orElseThrow();
        var outcome = new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("100"), new BigDecimal("100"));

        completion.complete(expired, "worker-a", outcome, started.plusSeconds(12));

        assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_homework_submission WHERE submission_id = ?", String.class, submissionId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox", Integer.class)).isZero();

        completion.complete(replacement, "worker-b", outcome, started.plusSeconds(12));

        assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_homework_submission WHERE submission_id = ?", String.class, submissionId)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox", Integer.class)).isEqualTo(2);
        completion.complete(expired, "worker-a", outcome, started.plusSeconds(13));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox", Integer.class)).isEqualTo(2);
    }
}
