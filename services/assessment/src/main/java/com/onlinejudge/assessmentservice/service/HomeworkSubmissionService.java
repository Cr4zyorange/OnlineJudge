package com.onlinejudge.assessmentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

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

    /** Objective answers are evaluated synchronously against the Assessment-owned answer key. */
    @Transactional
    public SubmittedHomework submitObjective(long homeworkId, String studentId, String answerJson) {
        HomeworkRule homework = lockHomework(homeworkId);
        Instant now = clock.instant();
        if (!"OBJECTIVE".equals(homework.type())) throw new IllegalArgumentException("objective submission requires an OBJECTIVE homework");
        validateOpenAndResubmission(homework, studentId, now);
        if (answerJson == null || answerJson.isBlank()) throw new IllegalArgumentException("answerJson is required");
        JsonNode submitted;
        try { submitted = new ObjectMapper().readTree(answerJson); }
        catch (java.io.IOException invalid) { throw new IllegalArgumentException("answerJson must be valid JSON", invalid); }
        if (submitted == null || !submitted.isObject()) throw new IllegalArgumentException("answerJson must be a JSON object");
        List<ObjectiveQuestion> questions = jdbc.query("""
                SELECT answer_json, score, sort_order
                  FROM assessment_homework_question
                 WHERE homework_id = ?
                 ORDER BY sort_order, id
                """, (rs, ignored) -> new ObjectiveQuestion(rs.getString("answer_json"), rs.getBigDecimal("score"),
                rs.getInt("sort_order")), homeworkId);
        BigDecimal score = BigDecimal.ZERO;
        try {
            ObjectMapper mapper = new ObjectMapper();
            for (ObjectiveQuestion question : questions) {
                JsonNode expected = mapper.readTree(question.answerJson());
                JsonNode actual = submitted.get("q" + question.sortOrder());
                if (actual != null && expected.equals(actual)) score = score.add(question.score());
            }
        } catch (java.io.IOException invalidKey) {
            throw new IllegalStateException("objective answer key is invalid", invalidKey);
        }
        int version = nextVersion(homeworkId, studentId);
        String submitStatus = now.isAfter(homework.deadline()) ? "LATE" : "SUBMITTED";
        String internalSubmissionId = java.util.UUID.randomUUID().toString();
        String status = score.compareTo(homework.totalScore()) == 0 ? "ACCEPTED" : "WRONG_ANSWER";
        jdbc.update("""
                INSERT INTO assessment_submission (id, source_type, source_id, course_id, student_id, content_ref,
                    evaluation_status, created_at) VALUES (?, 'HWK', ?, ?, ?, ?, ?, ?)
                """, internalSubmissionId, Long.toString(homeworkId), homework.courseId(), studentId,
                "objective://" + internalSubmissionId, status, Timestamp.from(now));
        supersedeCurrentSubmission(homeworkId, studentId);
        jdbc.update("""
                INSERT INTO assessment_homework_submission
                    (submission_id, homework_id, student_id, submission_version, submit_type, language, answer_json,
                     submit_status, evaluation_status, review_status, auto_score, final_score, is_final, submitted_at)
                VALUES (?, ?, ?, ?, 'OBJECTIVE', '', ?, ?, ?, 'REVIEWED', ?, ?, TRUE, ?)
                """, internalSubmissionId, homeworkId, studentId, version, answerJson, submitStatus, status,
                score, score, Timestamp.from(now));
        long publicSubmissionId = publicId(internalSubmissionId);
        return new SubmittedHomework(internalSubmissionId, null, publicSubmissionId, homeworkId, version,
                submitStatus, status, now);
    }

    /** A FILE submission binds exactly one previously uploaded, student-owned attachment. */
    @Transactional
    public SubmittedHomework submitFile(long homeworkId, String studentId, List<String> fileIds) {
        HomeworkRule homework = lockHomework(homeworkId);
        Instant now = clock.instant();
        if (!"FILE".equals(homework.type())) throw new IllegalArgumentException("file submission requires a FILE homework");
        validateOpenAndResubmission(homework, studentId, now);
        if (fileIds == null || fileIds.size() != 1 || fileIds.getFirst() == null || fileIds.getFirst().isBlank()) {
            throw new IllegalArgumentException("file submission requires exactly one attachment");
        }
        String fileId;
        try {
            fileId = java.util.UUID.fromString(fileIds.getFirst().trim()).toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("attachment id is invalid", invalid);
        }
        UploadedAttachment attachment = jdbc.query("""
                SELECT file_id, storage_key, expires_at
                  FROM assessment_homework_attachment
                 WHERE file_id = ? AND homework_id = ? AND uploader_id = ? AND status = 'UPLOADED'
                 FOR UPDATE
                """, (rs, ignored) -> new UploadedAttachment(rs.getString("file_id"), rs.getString("storage_key"),
                rs.getTimestamp("expires_at").toInstant()), fileId, homeworkId, studentId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("uploaded attachment was not found"));
        if (!attachment.expiresAt().isAfter(now)) throw new IllegalStateException("uploaded attachment has expired");

        int version = nextVersion(homeworkId, studentId);
        String submitStatus = now.isAfter(homework.deadline()) ? "LATE" : "SUBMITTED";
        String internalSubmissionId = java.util.UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO assessment_submission (id, source_type, source_id, course_id, student_id, content_ref,
                    evaluation_status, created_at) VALUES (?, 'HWK', ?, ?, ?, ?, 'NONE', ?)
                """, internalSubmissionId, Long.toString(homeworkId), homework.courseId(), studentId,
                attachment.storageKey(), Timestamp.from(now));
        supersedeCurrentSubmission(homeworkId, studentId);
        jdbc.update("""
                INSERT INTO assessment_homework_submission
                    (submission_id, homework_id, student_id, submission_version, submit_type, language,
                     submit_status, evaluation_status, review_status, is_final, submitted_at)
                VALUES (?, ?, ?, ?, 'FILE', '', ?, 'NONE', 'UNREVIEWED', TRUE, ?)
                """, internalSubmissionId, homeworkId, studentId, version, submitStatus, Timestamp.from(now));
        int bound = jdbc.update("""
                UPDATE assessment_homework_attachment
                   SET status = 'SUBMITTED', submission_id = ?, updated_at = ?
                 WHERE file_id = ? AND status = 'UPLOADED'
                """, internalSubmissionId, Timestamp.from(now), attachment.fileId());
        if (bound != 1) throw new IllegalStateException("uploaded attachment could not be bound to submission");
        long publicSubmissionId = publicId(internalSubmissionId);
        return new SubmittedHomework(internalSubmissionId, null, publicSubmissionId, homeworkId, version,
                submitStatus, "NONE", now);
    }

    /** Stores an upload and compensates the physical object if its database transaction fails. */
    @Transactional
    public AttachmentUpload uploadFile(long homeworkId, String studentId, String originalFilename, String contentType,
            byte[] content) {
        HomeworkRule homework = lockHomework(homeworkId);
        if (!"FILE".equals(homework.type()) || !"PUBLISHED".equals(homework.status())) {
            throw new IllegalStateException("homework does not accept attachments");
        }
        if (content == null || content.length == 0) throw new IllegalArgumentException("attachment content is required");
        Instant now = clock.instant();
        String fileId = java.util.UUID.randomUUID().toString();
        PersistentSubmissionFileStore.StoredFile stored;
        try {
            stored = files.store("hwk-" + fileId, originalFilename, content);
        } catch (IOException storageFailure) {
            throw new UncheckedIOException("attachment storage unavailable", storageFailure);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { files.delete(stored.storageKey()); } catch (IOException ignored) { }
                }
            }
        });
        Instant expiresAt = now.plus(java.time.Duration.ofHours(24));
        jdbc.update("""
                INSERT INTO assessment_homework_attachment
                    (file_id, homework_id, course_id, uploader_id, storage_key, original_filename, content_type, file_size,
                     status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED', ?, ?, ?)
                """, fileId, homeworkId, homework.courseId(), studentId, stored.storageKey(), stored.originalFilename(),
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                stored.size(), Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now));
        return new AttachmentUpload(fileId, stored.originalFilename(),
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                stored.size(), expiresAt, now);
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

    private record ObjectiveQuestion(String answerJson, BigDecimal score, int sortOrder) { }

    private record UploadedAttachment(String fileId, String storageKey, Instant expiresAt) { }

    public record AttachmentUpload(String fileId, String originalFilename, String contentType, long fileSize,
                                   Instant expiresAt, Instant uploadedAt) { }

    public record SubmittedHomework(String submissionId, String taskId, long publicSubmissionId, long homeworkId, int version,
                                    String submitStatus, String evaluationStatus, Instant submittedAt) { }

    public record SubmissionView(String internalSubmissionId, long publicSubmissionId, long homeworkId, String courseId,
                                 String studentId, String submitType, String answerText, String answerJson, String language,
                                 String submitStatus, String evaluationStatus, String reviewStatus, BigDecimal autoScore,
                                 BigDecimal manualScore, BigDecimal finalScore, String comment, int version,
                                 boolean finalSubmission, Instant submittedAt) { }
}
