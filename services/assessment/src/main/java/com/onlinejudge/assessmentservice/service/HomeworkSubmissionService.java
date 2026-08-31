package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HomeworkSubmissionService {
    private final JdbcTemplate jdbc;
    private final AssessmentSubmissionService submissions;
    private final PersistentSubmissionFileStore files;
    private final SourceGradeRepository grades;
    private final AssessmentOutboxRepository outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public HomeworkSubmissionService(JdbcTemplate jdbc, AssessmentSubmissionService submissions,
            PersistentSubmissionFileStore files, SourceGradeRepository grades, AssessmentOutboxRepository outbox) {
        this(jdbc, submissions, files, grades, outbox, Clock.systemUTC());
    }

    HomeworkSubmissionService(JdbcTemplate jdbc, AssessmentSubmissionService submissions,
            PersistentSubmissionFileStore files, SourceGradeRepository grades, AssessmentOutboxRepository outbox, Clock clock) {
        this.jdbc = jdbc;
        this.submissions = submissions;
        this.files = files;
        this.grades = grades;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Code asset, HWK version, generic submission and durable PENDING task form one local transaction. */
    @Transactional
    public SubmittedHomework submit(long homeworkId, String studentId, String code, String language) {
        HomeworkRule homework = lockHomework(homeworkId);
        Instant now = clock.instant();
        validate(homework, studentId, code, language, now);
        int version = jdbc.queryForObject("""
                SELECT COUNT(*) + 1 FROM assessment_homework_submission
                 WHERE homework_id = ? AND student_id = ?
                """, Integer.class, homeworkId, studentId);
        String submitStatus = now.isAfter(homework.deadline()) ? "LATE" : "SUBMITTED";
        PersistentSubmissionFileStore.StoredFile stored;
        try {
            stored = files.store(java.util.UUID.randomUUID().toString(), "submission-" + language + ".txt",
                    code.getBytes(StandardCharsets.UTF_8));
        } catch (IOException storageFailure) {
            throw new UncheckedIOException("submission storage unavailable", storageFailure);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { files.delete(stored.storageKey()); } catch (IOException ignored) { }
                }
            }
        });
        AssessmentSubmissionService.SubmittedSubmission generic = submissions.submit(
                new AssessmentSubmissionService.SubmissionCommand("HWK", Long.toString(homeworkId),
                        homework.courseId(), studentId, stored.storageKey()));
        jdbc.update("UPDATE assessment_homework_submission SET is_final = FALSE WHERE homework_id = ? AND student_id = ? AND is_final = TRUE",
                homeworkId, studentId);
        jdbc.update("""
                    INSERT INTO assessment_homework_submission
                        (submission_id, homework_id, student_id, submission_version, language, submit_status,
                         evaluation_status, is_final, submitted_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', TRUE, ?)
                """, generic.submissionId(), homeworkId, studentId, version, language, submitStatus, Timestamp.from(now));
        long publicSubmissionId = jdbc.queryForObject(
                "SELECT public_id FROM assessment_homework_submission WHERE submission_id = ?", Long.class,
                generic.submissionId());
        grades.markUngradedIfPresent("HWK", Long.toString(homeworkId), studentId, now)
                .ifPresent(grade -> appendUngradedEvent(grade, generic.taskId(), now));
        return new SubmittedHomework(generic.submissionId(), generic.taskId(), publicSubmissionId, homeworkId, version,
                submitStatus, "PENDING", now);
    }

    private void appendUngradedEvent(SourceGradeRepository.SourceGrade grade, String correlationId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", grade.courseId());
        payload.put("sourceType", grade.sourceType());
        payload.put("sourceId", grade.sourceId());
        payload.put("studentId", grade.studentId());
        payload.put("score", null);
        payload.put("fullScore", grade.fullScore());
        payload.put("status", "UNGRADED");
        payload.put("sourceVersion", grade.sourceVersion());
        outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade",
                grade.sourceType() + ":" + grade.sourceId() + ":" + grade.studentId(), grade.sourceVersion(),
                correlationId, payload, now);
    }

    private HomeworkRule lockHomework(long homeworkId) {
        return jdbc.query("""
                SELECT id, course_id, status, deadline, allow_resubmit, allow_late_submit, allowed_languages
                  FROM assessment_homework WHERE id = ? FOR UPDATE
                """, (rs, ignored) -> new HomeworkRule(rs.getLong("id"), rs.getString("course_id"), rs.getString("status"),
                rs.getTimestamp("deadline").toInstant(), rs.getBoolean("allow_resubmit"),
                rs.getBoolean("allow_late_submit"), rs.getString("allowed_languages")), homeworkId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("homework not found"));
    }

    private void validate(HomeworkRule homework, String studentId, String code, String language, Instant now) {
        if (!"PUBLISHED".equals(homework.status())) throw new IllegalStateException("homework is not open for submission");
        if (now.isAfter(homework.deadline()) && !homework.allowLateSubmit()) throw new IllegalStateException("homework deadline has passed");
        if (code == null || code.isBlank() || language == null || language.isBlank()) throw new IllegalArgumentException("code and language are required");
        if (Arrays.stream(homework.allowedLanguages().split(",")).noneMatch(language::equals)) throw new IllegalArgumentException("language is not allowed");
        int existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_homework_submission
                 WHERE homework_id = ? AND student_id = ? AND is_final = TRUE
                """, Integer.class, homework.id(), studentId);
        if (existing > 0 && !homework.allowResubmit()) throw new IllegalStateException("homework does not allow resubmission");
    }

    private record HomeworkRule(long id, String courseId, String status, Instant deadline, boolean allowResubmit,
                                boolean allowLateSubmit, String allowedLanguages) { }

    public record SubmittedHomework(String submissionId, String taskId, long publicSubmissionId, long homeworkId, int version,
                                    String submitStatus, String evaluationStatus, Instant submittedAt) { }
}
