package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** Terminal write, source-grade mutation and outbox facts commit together after sandbox execution. */
@Component
class WorkerCompletionService {
    private final EvaluationTaskRepository tasks; private final AssessmentOutboxRepository outbox; private final SourceGradeRepository grades;
    WorkerCompletionService(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades) { this.tasks = tasks; this.outbox = outbox; this.grades = grades; }
    @Transactional
    void complete(EvaluationTask task, String workerId, AssessmentWorker.EvaluationOutcome outcome, Instant finished) {
        if (!tasks.complete(task.id(), workerId, task.generation(), outcome.successful(), outcome.status(), finished)) return;
        outbox.append("assessment.evaluation.completed.v2", "assessment-submission", task.submissionId(), task.generation(), task.id(),
                Map.of("courseId", task.courseId(), "submissionId", task.submissionId(), "evaluationStatus", outcome.successful() ? "SUCCESS" : "FAILED", "evaluationVersion", task.generation(), "completedAt", finished.toString()), finished);
        if (outcome.successful()) {
            long version = grades.upsertScored(task.sourceType(), task.sourceId(), task.courseId(), task.studentId(), outcome.score(), outcome.fullScore(), finished);
            outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", task.sourceType() + ":" + task.sourceId() + ":" + task.studentId(), version, task.id(),
                    Map.of("courseId", task.courseId(), "sourceType", task.sourceType(), "sourceId", task.sourceId(), "studentId", task.studentId(), "score", outcome.score(), "fullScore", outcome.fullScore(), "status", "SCORED", "sourceVersion", version), finished);
        }
    }
}
