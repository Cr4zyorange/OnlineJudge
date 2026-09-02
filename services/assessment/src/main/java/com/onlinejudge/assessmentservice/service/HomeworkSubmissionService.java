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
import java.math.BigDecimal;
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
        int version = nextVersion(homeworkId, studentId);
        String submitStatus = now.isAfter(homework.deadline()) ? "LATE" : "SUBMITTED";
        PersistentSubmissionFileStore.StoredFile stored;
        try {
            stored = files.store(java.util.UUID.randomUUID().toString(), sourceFilename(language),
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
        supersedeCurrentSubmission(homeworkId, studentId);
        jdbc.update("""
                    INSERT INTO assessment_homework_submission
                        (submission_id, homework_id, student_id, submission_version, submit_type, language, submit_status,
                         evaluation_status, review_status, is_final, submitted_at)
                    VALUES (?, ?, ?, ?, 'CODE', ?, ?, 'PENDING', 'NEED_REVIEW', TRUE, ?)
                """, generic.submissionId(), homeworkId, studentId, version, language, submitStatus, Timestamp.from(now));
        long publicSubmissionId = publicId(generic.submissionId());
        grades.markUngradedIfPresent("HWK", Long.toString(homeworkId), studentId, now)
                .ifPresent(grade -> appendUngradedEvent(grade, generic.taskId(), now));
        return new SubmittedHomework(generic.submissionId(), generic.taskId(), publicSubmissionId, homeworkId, version,
                submitStatus, "PENDING", now);
    }

    /** TEXT submissions are durable business facts but deliberately do not enqueue a sandbox task. */
    @Transactional
    public SubmittedHomework submitText(long homeworkId, String studentId, String answerText) {
        HomeworkRule homework = lockHomework(homeworkId);
        Instant now = clock.instant();
        if (!"TEXT".equals(homework.type())) throw new IllegalArgumentException("text submission requires a TEXT homework");
        validateOpenAndResubmission(homework, studentId, now);
        if (answerText == null || answerText.isBlank()) throw new IllegalArgumentException("answerText is required");
        int version = nextVersion(homeworkId, studentId);
        String submitStatus = now.isAfter(homework.deadline()) ? "LATE" : "SUBMITTED";
        String internalSubmissionId = java.util.UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO assessment_submission (id, source_type, source_id, course_id, student_id, content_ref,
                    evaluation_status, created_at) VALUES (?, 'HWK', ?, ?, ?, ?, 'NONE', ?)
                """, internalSubmissionId, Long.toString(homeworkId), homework.courseId(), studentId,
                "text://" + internalSubmissionId, Timestamp.from(now));
        supersedeCurrentSubmission(homeworkId, studentId);
        jdbc.update("""
                    INSERT INTO assessment_homework_submission
                        (submission_id, homework_id, student_id, submission_version, submit_type, language, answer_text,
                         submit_status, evaluation_status, review_status, is_final, submitted_at)
                    VALUES (?, ?, ?, ?, 'TEXT', '', ?, ?, 'NONE', 'UNREVIEWED', TRUE, ?)
                """, internalSubmissionId, homeworkId, studentId, version, answerText.trim(), submitStatus, Timestamp.from(now));
        long publicSubmissionId = publicId(internalSubmissionId);
        return new SubmittedHomework(internalSubmissionId, null, publicSubmissionId, homeworkId, version,
                submitStatus, "NONE", now);
    }

    @Transactional
    public SubmissionView review(long publicSubmissionId, String managerId, BigDecimal manualScore,
                                 BigDecimal finalScore, String comment) {
        SubmissionView existing = find(publicSubmissionId);
        HomeworkRule homework = lockHomework(existing.homeworkId());
        if (!"TEXT".equals(existing.submitType())) {
            throw new IllegalStateException("manual review currently supports text homework submissions");
        }
        if (manualScore == null || finalScore == null || manualScore.signum() < 0 || finalScore.signum() < 0
                || manualScore.compareTo(homework.totalScore()) > 0 || finalScore.compareTo(homework.totalScore()) > 0) {
            throw new IllegalArgumentException("manualScore and finalScore must be within homework totalScore");
        }
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE assessment_homework_submission
                   SET manual_score = ?, final_score = ?, review_comment = ?, review_status = 'REVIEWED'
                 WHERE public_id = ?
                """, manualScore, finalScore, comment == null ? "" : comment.trim(), publicSubmissionId);
        jdbc.update("""
                INSERT INTO assessment_homework_review_log
                    (submission_id, homework_id, student_id, operation_type, old_score, new_score, operator_id, reason, created_at)
                VALUES (?, ?, ?, 'REVIEW', ?, ?, ?, ?, ?)
                """, existing.internalSubmissionId(), existing.homeworkId(), existing.studentId(), existing.finalScore(),
                finalScore, managerId, comment == null ? "" : comment.trim(), Timestamp.from(now));
        return find(publicSubmissionId);
    }

    public SubmissionView find(long publicSubmissionId) {
        return jdbc.query("""
                SELECT hs.submission_id, hs.public_id, hs.homework_id, h.course_id, hs.student_id, hs.submit_type,
                       hs.answer_text, hs.answer_json, hs.language, hs.submit_status, hs.evaluation_status,
                       hs.review_status, hs.auto_score, hs.manual_score, hs.final_score, hs.review_comment,
                       hs.submission_version, hs.is_final, hs.submitted_at
                  FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.public_id = ?
                """, (rs, ignored) -> mapSubmission(rs), publicSubmissionId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("homework submission not found"));
    }

    public java.util.List<SubmissionView> listForHomework(long homeworkId, String studentId) {
        return jdbc.query("""
                SELECT hs.submission_id, hs.public_id, hs.homework_id, h.course_id, hs.student_id, hs.submit_type,
                       hs.answer_text, hs.answer_json, hs.language, hs.submit_status, hs.evaluation_status,
                       hs.review_status, hs.auto_score, hs.manual_score, hs.final_score, hs.review_comment,
                       hs.submission_version, hs.is_final, hs.submitted_at
                  FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.homework_id = ? AND hs.student_id = ?
                 ORDER BY hs.submission_version DESC
                """, (rs, ignored) -> mapSubmission(rs), homeworkId, studentId);
    }

    public java.util.List<SubmissionView> listForManager(long homeworkId) {
        return jdbc.query("""
                SELECT hs.submission_id, hs.public_id, hs.homework_id, h.course_id, hs.student_id, hs.submit_type,
                       hs.answer_text, hs.answer_json, hs.language, hs.submit_status, hs.evaluation_status,
                       hs.review_status, hs.auto_score, hs.manual_score, hs.final_score, hs.review_comment,
                       hs.submission_version, hs.is_final, hs.submitted_at
                  FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.homework_id = ?
                 ORDER BY hs.submitted_at DESC, hs.public_id DESC
                """, (rs, ignored) -> mapSubmission(rs), homeworkId);
    }

    private static SubmissionView mapSubmission(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SubmissionView(rs.getString("submission_id"), rs.getLong("public_id"), rs.getLong("homework_id"),
                rs.getString("course_id"), rs.getString("student_id"), rs.getString("submit_type"),
                rs.getString("answer_text"), rs.getString("answer_json"), rs.getString("language"),
                rs.getString("submit_status"), rs.getString("evaluation_status"), rs.getString("review_status"),
                rs.getBigDecimal("auto_score"), rs.getBigDecimal("manual_score"), rs.getBigDecimal("final_score"),
                rs.getString("review_comment"), rs.getInt("submission_version"), rs.getBoolean("is_final"),
                rs.getTimestamp("submitted_at").toInstant());
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
                SELECT id, course_id, type, status, deadline, allow_resubmit, allow_late_submit, allowed_languages, total_score
                  FROM assessment_homework WHERE id = ? FOR UPDATE
                """, (rs, ignored) -> new HomeworkRule(rs.getLong("id"), rs.getString("course_id"), rs.getString("type"), rs.getString("status"),
                rs.getTimestamp("deadline").toInstant(), rs.getBoolean("allow_resubmit"),
                rs.getBoolean("allow_late_submit"), rs.getString("allowed_languages"), rs.getBigDecimal("total_score")), homeworkId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("homework not found"));
    }

    private void validate(HomeworkRule homework, String studentId, String code, String language, Instant now) {
        if (!"CODE".equals(homework.type())) throw new IllegalArgumentException("code submission requires a CODE homework");
        validateOpenAndResubmission(homework, studentId, now);
        if (code == null || code.isBlank() || language == null || language.isBlank()) throw new IllegalArgumentException("code and language are required");
        if (Arrays.stream(homework.allowedLanguages().split(",")).noneMatch(language::equals)) throw new IllegalArgumentException("language is not allowed");
    }

    private void validateOpenAndResubmission(HomeworkRule homework, String studentId, Instant now) {
        if (!"PUBLISHED".equals(homework.status())) throw new IllegalStateException("homework is not open for submission");
        if (now.isAfter(homework.deadline()) && !homework.allowLateSubmit()) throw new IllegalStateException("homework deadline has passed");
        int existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_homework_submission
                 WHERE homework_id = ? AND student_id = ? AND is_final = TRUE
                """, Integer.class, homework.id(), studentId);
        if (existing > 0 && !homework.allowResubmit()) throw new IllegalStateException("homework does not allow resubmission");
    }

    private int nextVersion(long homeworkId, String studentId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) + 1 FROM assessment_homework_submission
                 WHERE homework_id = ? AND student_id = ?
                """, Integer.class, homeworkId, studentId);
    }

    private void supersedeCurrentSubmission(long homeworkId, String studentId) {
        jdbc.update("UPDATE assessment_homework_submission SET is_final = FALSE WHERE homework_id = ? AND student_id = ? AND is_final = TRUE",
                homeworkId, studentId);
    }

    private long publicId(String internalSubmissionId) {
        return jdbc.queryForObject("SELECT public_id FROM assessment_homework_submission WHERE submission_id = ?", Long.class,
                internalSubmissionId);
    }

    /** The persisted name is the runtime selector for the shared Docker evaluator. */
    private static String sourceFilename(String language) {
        return switch (language) {
            case "python" -> "submission.py";
            case "java" -> "Main.java";
            case "cpp", "c++", "cc", "cxx" -> "main.cpp";
            default -> "submission.unknown";
        };
    }

    private record HomeworkRule(long id, String courseId, String type, String status, Instant deadline, boolean allowResubmit,
                                boolean allowLateSubmit, String allowedLanguages, BigDecimal totalScore) { }

    public record SubmittedHomework(String submissionId, String taskId, long publicSubmissionId, long homeworkId, int version,
                                    String submitStatus, String evaluationStatus, Instant submittedAt) { }

    public record SubmissionView(String internalSubmissionId, long publicSubmissionId, long homeworkId, String courseId,
                                 String studentId, String submitType, String answerText, String answerJson, String language,
                                 String submitStatus, String evaluationStatus, String reviewStatus, BigDecimal autoScore,
                                 BigDecimal manualScore, BigDecimal finalScore, String comment, int version,
                                 boolean finalSubmission, Instant submittedAt) { }
}
