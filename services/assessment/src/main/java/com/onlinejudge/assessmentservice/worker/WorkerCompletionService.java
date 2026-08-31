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
        if (jdbc != null && "HWK".equals(task.sourceType())) {
            jdbc.update("UPDATE assessment_submission SET evaluation_status = ? WHERE id = ?", outcome.status(), task.submissionId());
            jdbc.update("""
                    UPDATE assessment_homework_submission
                       SET evaluation_status = ?, auto_score = ?, final_score = ?
                     WHERE submission_id = ?
                    """, outcome.status(), outcome.successful() ? outcome.score() : null,
                    outcome.successful() ? outcome.score() : null, task.submissionId());
        }
        outbox.append("assessment.evaluation.completed.v2", "assessment-submission", task.submissionId(), task.generation(), task.id(),
                Map.of("courseId", task.courseId(), "submissionId", task.submissionId(), "evaluationStatus", outcome.successful() ? "SUCCESS" : "FAILED", "evaluationVersion", task.generation(), "completedAt", finished.toString()), finished);
        if (outcome.successful()) {
            long version = grades.upsertScored(task.sourceType(), task.sourceId(), task.courseId(), task.studentId(), outcome.score(), outcome.fullScore(), finished);
            outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", task.sourceType() + ":" + task.sourceId() + ":" + task.studentId(), version, task.id(),
                    Map.of("courseId", task.courseId(), "sourceType", task.sourceType(), "sourceId", task.sourceId(), "studentId", task.studentId(), "score", outcome.score(), "fullScore", outcome.fullScore(), "status", "SCORED", "sourceVersion", version), finished);
        }
    }
    private boolean retryable(String status) { return "SANDBOX_TIMEOUT".equals(status) || "SANDBOX_ERROR".equals(status) || "SYSTEM_ERROR".equals(status); }
}
