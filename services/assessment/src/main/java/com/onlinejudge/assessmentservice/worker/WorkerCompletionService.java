package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

/** Terminal write, source-grade mutation and outbox facts commit together after sandbox execution. */
@Component
class WorkerCompletionService {
    private final EvaluationTaskRepository tasks; private final AssessmentOutboxRepository outbox; private final SourceGradeRepository grades;
    private final JdbcTemplate jdbc;
    private final int maxAttempts; private final Duration retryBackoff;
    @org.springframework.beans.factory.annotation.Autowired
    WorkerCompletionService(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades,
            JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${assessment.worker.max-attempts:3}") int maxAttempts,
            @org.springframework.beans.factory.annotation.Value("${assessment.worker.retry-backoff:PT5S}") Duration retryBackoff) {
        if (maxAttempts < 1 || retryBackoff.isNegative()) throw new IllegalArgumentException("worker retry policy must have a positive bound and non-negative backoff");
        this.tasks = tasks; this.outbox = outbox; this.grades = grades; this.jdbc = jdbc; this.maxAttempts = maxAttempts; this.retryBackoff = retryBackoff;
    }
    WorkerCompletionService(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades) {
        this(tasks, outbox, grades, null, 3, Duration.ofSeconds(5));
    }
    @Transactional
    void complete(EvaluationTask task, String workerId, AssessmentWorker.EvaluationOutcome outcome, Instant finished) {
        if (!outcome.successful() && retryable(outcome.status()) && task.attempt() < maxAttempts) {
            tasks.reschedule(task.id(), workerId, task.generation(), outcome.status(), finished.plus(retryBackoff), finished);
            return;
        }
        if (!tasks.complete(task.id(), workerId, task.generation(), outcome.successful(), outcome.status(), finished)) return;
        // The lightweight constructor is used by queue-only tests and has no
        // HWK projection.  Preserve its pre-existing generic-task behaviour.
        boolean homeworkProjection = false;
        if (jdbc != null) {
            if ("LAB".equals(task.sourceType())) {
                jdbc.update("UPDATE assessment_submission SET evaluation_status = ? WHERE id = ?", outcome.status(), task.submissionId());
                jdbc.update("UPDATE assessment_lab_submission SET auto_score = ? WHERE submission_id = ?", outcome.successful() ? outcome.score() : null, task.submissionId());
                jdbc.update("DELETE FROM assessment_lab_evaluation_result WHERE submission_id = ?", task.submissionId());
                for (AssessmentWorker.LabCaseResult result : outcome.caseResults()) {
                    jdbc.update("""
                            INSERT INTO assessment_lab_evaluation_result (submission_id, testcase_id, passed, score, actual_output, message, executed_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, task.submissionId(), result.testcaseId(), result.passed(), result.score(), result.actualOutput(),
                            result.message(), java.sql.Timestamp.from(finished));
                }
            }
            if ("HWK".equals(task.sourceType())) {
            homeworkProjection = !jdbc.query("""
                    SELECT is_final FROM assessment_homework_submission
                     WHERE submission_id = ? FOR UPDATE
                    """, (rs, ignored) -> rs.getBoolean("is_final"), task.submissionId()).isEmpty();
            if (homeworkProjection) {
                jdbc.update("UPDATE assessment_submission SET evaluation_status = ? WHERE id = ?", outcome.status(), task.submissionId());
                jdbc.update("""
                        UPDATE assessment_homework_submission
                           SET evaluation_status = ?, auto_score = ?, final_score = ?
                         WHERE submission_id = ?
                        """, outcome.status(), outcome.successful() ? outcome.score() : null,
                        outcome.successful() ? outcome.score() : null, task.submissionId());
                appendHomeworkEvaluation(task, outcome, finished);
            }
            }
        }
        outbox.append("assessment.evaluation.completed.v2", "assessment-submission", task.submissionId(), task.generation(), task.id(),
                Map.of("courseId", task.courseId(), "submissionId", task.submissionId(), "evaluationStatus", outcome.successful() ? "SUCCESS" : "FAILED", "evaluationVersion", task.generation(), "completedAt", finished.toString()), finished);
        if (outcome.successful() && (!"HWK".equals(task.sourceType()) || !homeworkProjection)
                && shouldPublishSourceGrade(task)) {
            java.math.BigDecimal publishedScore = outcome.score();
            if ("LAB".equals(task.sourceType()) && jdbc != null) {
                // Release selects the newest finalized submission for a student;
                // replaying a later unfinalized submission must preserve that basis.
                java.math.BigDecimal finalScore = jdbc.query("""
                        SELECT final_score FROM assessment_lab_submission
                         WHERE lab_id = ? AND student_id = ? AND final_score IS NOT NULL
                         ORDER BY submission_version DESC, submitted_at DESC
                         LIMIT 1
                        """, rows -> rows.next() ? rows.getBigDecimal(1) : null,
                        Long.parseLong(task.sourceId()), task.studentId());
                if (finalScore != null) publishedScore = finalScore;
            }
            long version = grades.upsertScored(task.sourceType(), task.sourceId(), task.courseId(), task.studentId(), publishedScore, outcome.fullScore(), finished);
            outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", task.sourceType() + ":" + task.sourceId() + ":" + task.studentId(), version, task.id(),
                    Map.of("courseId", task.courseId(), "sourceType", task.sourceType(), "sourceId", task.sourceId(), "studentId", task.studentId(), "score", publishedScore, "fullScore", outcome.fullScore(), "status", "SCORED", "sourceVersion", version), finished);
        }
    }
    private void appendHomeworkEvaluation(EvaluationTask task, AssessmentWorker.EvaluationOutcome outcome, Instant finished) {
        jdbc.update("""
                INSERT INTO assessment_homework_evaluation
                    (task_id, task_generation, submission_id, homework_id, student_id, evaluation_type, status,
                     score, full_score, started_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, task.id(), task.generation(), task.submissionId(), Long.parseLong(task.sourceId()), task.studentId(),
                task.generation() > 1 ? "REJUDGE" : "CODE_JUDGE", outcome.status(),
                outcome.successful() ? outcome.score() : null, outcome.fullScore(), finished, finished);
    }

    private boolean shouldPublishSourceGrade(EvaluationTask task) {
        if (!"LAB".equals(task.sourceType())) return true;
        if (jdbc == null) return false;
        String status = jdbc.query("SELECT status FROM assessment_lab_experiment WHERE id = ?",
                rows -> rows.next() ? rows.getString(1) : null, Long.parseLong(task.sourceId()));
        return "SCORE_PUBLISHED".equals(status) || "ARCHIVED".equals(status);
    }
    private boolean retryable(String status) { return "SANDBOX_TIMEOUT".equals(status) || "SANDBOX_ERROR".equals(status) || "SYSTEM_ERROR".equals(status); }
}
