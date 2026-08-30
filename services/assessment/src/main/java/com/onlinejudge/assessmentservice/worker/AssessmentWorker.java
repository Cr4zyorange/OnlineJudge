package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Worker entrypoint: it is invoked by the worker workload, never by an HTTP GET. */
@Component
public class AssessmentWorker {
    private final EvaluationTaskRepository tasks;
    private final AssessmentOutboxRepository outbox;
    private final SourceGradeRepository grades;
    private final Clock clock;

    @Autowired
    public AssessmentWorker(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades) {
        this(tasks, outbox, grades, Clock.systemUTC());
    }
    AssessmentWorker(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades, Clock clock) {
        this.tasks = tasks; this.outbox = outbox; this.grades = grades; this.clock = clock;
    }

    @Transactional
    public Optional<EvaluationTask> runOne(String workerId, EvaluationExecutor executor) {
        Instant now = clock.instant();
        Optional<EvaluationTask> claimed = tasks.claimNext(workerId, now, Duration.ofSeconds(30));
        if (claimed.isEmpty()) return Optional.empty();
        EvaluationTask task = claimed.get();
        EvaluationOutcome outcome;
        try { outcome = executor.evaluate(task); }
        catch (Exception ignored) { outcome = EvaluationOutcome.failed("SYSTEM_ERROR"); }
        Instant finished = clock.instant();
        if (tasks.complete(task.id(), workerId, task.generation(), outcome.successful(), outcome.status(), finished)) {
            outbox.append("assessment.evaluation.completed.v2", "assessment-submission", task.submissionId(), task.generation(),
                    task.id(), Map.of("courseId", task.courseId(), "submissionId", task.submissionId(),
                            "evaluationStatus", outcome.successful() ? "SUCCESS" : "FAILED", "evaluationVersion", task.generation(),
                            "completedAt", finished.toString()), finished);
            if (outcome.successful()) {
                long version = grades.upsertScored(task.sourceType(), task.sourceId(), task.courseId(), task.studentId(), outcome.score(), outcome.fullScore(), finished);
                outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", task.sourceType() + ":" + task.sourceId() + ":" + task.studentId(), version,
                        task.id(), Map.of("courseId", task.courseId(), "sourceType", task.sourceType(), "sourceId", task.sourceId(), "studentId", task.studentId(),
                                "score", outcome.score(), "fullScore", outcome.fullScore(), "status", "SCORED", "sourceVersion", version), finished);
            }
        }
        return tasks.find(task.id());
    }

    public interface EvaluationExecutor { EvaluationOutcome evaluate(EvaluationTask task) throws Exception; }
    public record EvaluationOutcome(boolean successful, String status, java.math.BigDecimal score, java.math.BigDecimal fullScore) {
        public static EvaluationOutcome successful(String status) { return new EvaluationOutcome(true, status, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE); }
        public static EvaluationOutcome failed(String status) { return new EvaluationOutcome(false, status, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE); }
    }
}
