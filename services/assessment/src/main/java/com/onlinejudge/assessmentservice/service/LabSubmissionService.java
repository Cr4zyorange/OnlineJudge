package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * LAB keeps its versioned submission fact while reusing Assessment's durable
 * task table.  Both rows commit in one local transaction; a failed transaction
 * compensates the just-created storage object instead of returning a phantom submission.
 */
@Service
public class LabSubmissionService {
    private final JdbcTemplate jdbc;
    private final EvaluationTaskRepository tasks;
    private final PersistentSubmissionFileStore files;
    private final Clock clock;

    @Autowired
    public LabSubmissionService(JdbcTemplate jdbc, EvaluationTaskRepository tasks, PersistentSubmissionFileStore files) {
        this(jdbc, tasks, files, Clock.systemUTC());
    }

    LabSubmissionService(JdbcTemplate jdbc, EvaluationTaskRepository tasks, PersistentSubmissionFileStore files, Clock clock) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public SubmittedLabSubmission submit(SubmitLabCommand command) {
        Instant now = clock.instant();
        LabExperimentService.LabSummary lab = lockedLab(command.labId());
        if (command.courseId() != null && !command.courseId().isBlank() && !lab.courseId().equals(command.courseId())) {
            throw new IllegalArgumentException("courseId does not match LAB");
        }
        if (!"PUBLISHED".equals(lab.status()) || !now.isBefore(lab.deadline())) throw new IllegalStateException("LAB is not open for submissions");
        if (!languageAllowed(lab.labId(), command.language())) throw new IllegalArgumentException("language is not enabled for LAB");

        PersistentSubmissionFileStore.StoredFile stored = null;
        try {
            String submissionId = UUID.randomUUID().toString();
            boolean autoEvaluation = lab.autoEvaluate() && !"MANUAL".equalsIgnoreCase(lab.evaluationMode());
            String taskId = autoEvaluation ? UUID.randomUUID().toString() : null;
            String evaluationStatus = autoEvaluation ? "PENDING" : "NONE";
            stored = files.store(submissionId, command.originalFilename(), command.content());
            int version = nextVersion(lab.labId(), command.studentId());
            jdbc.update("""
                    INSERT INTO assessment_submission (id, source_type, source_id, course_id, student_id, content_ref,
                        evaluation_status, code_content, created_at) VALUES (?, 'LAB', ?, ?, ?, ?, ?, ?, ?)
                    """, submissionId, Long.toString(lab.labId()), lab.courseId(), command.studentId(), stored.storageKey(), evaluationStatus, command.codeContent(), Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO assessment_lab_submission (submission_id, lab_id, student_id, submission_version, language,
                        submit_status, has_file, submitted_at) VALUES (?, ?, ?, ?, ?, 'SUBMITTED', ?, ?)
                    """, submissionId, lab.labId(), command.studentId(), version, command.language(), command.hasFile(), Timestamp.from(now));
            if (autoEvaluation) {
                tasks.insert(taskId, submissionId, "LAB", Long.toString(lab.labId()), lab.courseId(), command.studentId(), command.originRequestId(), now);
            }
            return new SubmittedLabSubmission(submissionId, taskId, lab.labId(), command.studentId(), version, "SUBMITTED", evaluationStatus, now);
        } catch (RuntimeException | java.io.IOException failed) {
            if (stored != null) {
                try { files.delete(stored.storageKey()); }
                catch (java.io.IOException ignored) { /* residual storage cleanup is observable infrastructure work */ }
            }
            if (failed instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("submission storage unavailable", failed);
        }
    }

    private LabExperimentService.LabSummary lockedLab(long labId) {
        return jdbc.query("""
                SELECT id, course_id, title, status, deadline, max_score, auto_evaluate, evaluation_mode, created_at
                  FROM assessment_lab_experiment WHERE id = ? FOR UPDATE
                """, (rs, ignored) -> new LabExperimentService.LabSummary(rs.getLong("id"), rs.getString("course_id"),
                rs.getString("title"), rs.getString("status"), rs.getTimestamp("deadline").toInstant(),
                rs.getBigDecimal("max_score"), rs.getBoolean("auto_evaluate"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("evaluation_mode"), false, null, false), labId)
                .stream().findFirst().orElseThrow(() -> new java.util.NoSuchElementException("LAB does not exist"));
    }

    private int nextVersion(long labId, String studentId) {
        Integer current = jdbc.queryForObject("SELECT COALESCE(MAX(submission_version), 0) FROM assessment_lab_submission WHERE lab_id = ? AND student_id = ?",
                Integer.class, labId, studentId);
        return (current == null ? 0 : current) + 1;
    }

    private boolean languageAllowed(long labId, String language) {
        String configured = jdbc.queryForObject("SELECT allowed_languages FROM assessment_lab_experiment WHERE id = ?", String.class, labId);
        return configured != null && java.util.Arrays.stream(configured.split(",")).anyMatch(language::equalsIgnoreCase);
    }

    public record SubmitLabCommand(long labId, String courseId, String studentId, String language, String originalFilename, byte[] content,
                                   boolean hasFile, String codeContent, String originRequestId) {
        public SubmitLabCommand(long labId, String courseId, String studentId, String language, String originalFilename, byte[] content,
                                boolean hasFile, String codeContent) {
            this(labId, courseId, studentId, language, originalFilename, content, hasFile, codeContent, UUID.randomUUID().toString());
        }
        public SubmitLabCommand(long labId, String courseId, String studentId, String language, String originalFilename, byte[] content) {
            this(labId, courseId, studentId, language, originalFilename, content, true, null, UUID.randomUUID().toString());
        }
        public SubmitLabCommand {
            if (studentId == null || studentId.isBlank() || language == null || language.isBlank()) {
                throw new IllegalArgumentException("studentId and language are required");
            }
            if (content == null || content.length == 0) throw new IllegalArgumentException("submission file is required");
            if (originRequestId == null || originRequestId.isBlank() || originRequestId.length() > 80) throw new IllegalArgumentException("origin request id is required");
        }
    }
    public record SubmittedLabSubmission(String submissionId, String taskId, long labId, String studentId, int version,
                                         String submitStatus, String evaluationStatus, Instant submittedAt) { }
}
