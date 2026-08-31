package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AssessmentSubmissionService {
    private final JdbcTemplate jdbc;
    private final EvaluationTaskRepository tasks;
    private final Clock clock;

    @Autowired
    public AssessmentSubmissionService(JdbcTemplate jdbc, EvaluationTaskRepository tasks) {
        this(jdbc, tasks, Clock.systemUTC());
    }
    AssessmentSubmissionService(JdbcTemplate jdbc, EvaluationTaskRepository tasks, Clock clock) {
        this.jdbc = jdbc; this.tasks = tasks; this.clock = clock;
    }

    /** Submission and durable PENDING task are one local Assessment transaction. */
    @Transactional
    public SubmittedSubmission submit(SubmissionCommand command) {
        Instant now = clock.instant();
        String submissionId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO assessment_submission (id, source_type, source_id, course_id, student_id, content_ref,
                    evaluation_status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, submissionId, command.sourceType(), command.sourceId(), command.courseId(), command.studentId(),
                command.contentRef(), Timestamp.from(now));
        tasks.insert(taskId, submissionId, command.sourceType(), command.sourceId(), command.courseId(), command.studentId(), now);
        return new SubmittedSubmission(submissionId, taskId, "PENDING");
    }

    public record SubmissionCommand(String sourceType, String sourceId, String courseId, String studentId, String contentRef) {
        public SubmissionCommand {
            if (!"HWK".equals(sourceType)) throw new IllegalArgumentException("generic submissions only support HWK");
            if (sourceId == null || sourceId.isBlank() || courseId == null || courseId.isBlank() || studentId == null || studentId.isBlank()) {
                throw new IllegalArgumentException("sourceId, courseId and studentId are required");
            }
        }
    }
    public record SubmittedSubmission(String submissionId, String taskId, String evaluationStatus) { }
}
