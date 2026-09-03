package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Worker entrypoint: it is invoked by the worker workload, never by an HTTP GET. */
@Component
public class AssessmentWorker {
    private static final Logger log = LoggerFactory.getLogger(AssessmentWorker.class);
    private final EvaluationTaskRepository tasks;
    private final AssessmentOutboxRepository outbox;
    private final SourceGradeRepository grades;
    private final WorkerCompletionService completion;
    private final Clock clock;
    private final Duration lease;
    private final Duration heartbeatInterval;

    @Autowired
    public AssessmentWorker(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades, WorkerCompletionService completion,
            @org.springframework.beans.factory.annotation.Value("${assessment.worker.lease:PT30S}") Duration lease,
            @org.springframework.beans.factory.annotation.Value("${assessment.worker.heartbeat-interval:PT5S}") Duration heartbeatInterval) {
        this(tasks, outbox, grades, completion, Clock.systemUTC(), lease, heartbeatInterval);
    }
    AssessmentWorker(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades, WorkerCompletionService completion, Clock clock) {
        this(tasks, outbox, grades, completion, clock, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }
    AssessmentWorker(EvaluationTaskRepository tasks, AssessmentOutboxRepository outbox, SourceGradeRepository grades, WorkerCompletionService completion, Clock clock,
            Duration lease, Duration heartbeatInterval) {
        if (lease.isZero() || lease.isNegative() || heartbeatInterval.isZero() || heartbeatInterval.isNegative() || heartbeatInterval.compareTo(lease) >= 0) throw new IllegalArgumentException("worker heartbeat interval must be positive and shorter than its lease");
        this.tasks = tasks; this.outbox = outbox; this.grades = grades; this.completion = completion; this.clock = clock; this.lease = lease; this.heartbeatInterval = heartbeatInterval;
    }

    public Optional<EvaluationTask> runOne(String workerId, EvaluationExecutor executor) {
        Instant now = clock.instant();
        Optional<EvaluationTask> claimed = tasks.claimNext(workerId, now, lease);
        if (claimed.isEmpty()) return Optional.empty();
        EvaluationTask task = claimed.get();
        EvaluationOutcome outcome;
        outcome = evaluateWithHeartbeats(task, workerId, executor);
        Instant finished = clock.instant();
        completion.complete(task, workerId, outcome, finished);
        Optional<EvaluationTask> completed = tasks.find(task.id());
        completed.ifPresent(result -> log.info(
                "assessment_worker_terminal taskId={} submissionId={} sourceType={} taskState={} evaluationStatus={} score={}",
                result.id(), result.submissionId(), result.sourceType(), result.state(), outcome.status(), outcome.score()));
        return completed;
    }

    private EvaluationOutcome evaluateWithHeartbeats(EvaluationTask task, String workerId, EvaluationExecutor executor) {
        try (var execution = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("assessment-evaluator-", 0).factory())) {
            Future<EvaluationOutcome> future = execution.submit(() -> executor.evaluate(task));
            long waitMillis = Math.max(1, heartbeatInterval.toMillis());
            while (true) {
                try { return future.get(waitMillis, TimeUnit.MILLISECONDS); }
                catch (TimeoutException elapsed) {
                    if (!tasks.heartbeat(task.id(), workerId, task.generation(), clock.instant(), lease)) {
                        future.cancel(true);
                        return EvaluationOutcome.failed("LEASE_LOST");
                    }
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    return EvaluationOutcome.failed("WORKER_INTERRUPTED");
                }
                catch (ExecutionException failed) { return EvaluationOutcome.failed("SYSTEM_ERROR"); }
            }
        }
    }

    public interface EvaluationExecutor { EvaluationOutcome evaluate(EvaluationTask task) throws Exception; }
    public record EvaluationOutcome(boolean successful, String status, java.math.BigDecimal score, java.math.BigDecimal fullScore,
                                    java.util.List<LabCaseResult> caseResults) {
        public EvaluationOutcome(boolean successful, String status, java.math.BigDecimal score, java.math.BigDecimal fullScore) {
            this(successful, status, score, fullScore, java.util.List.of());
        }
        public static EvaluationOutcome successful(String status) { return new EvaluationOutcome(true, status, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE); }
        public static EvaluationOutcome failed(String status) { return new EvaluationOutcome(false, status, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE); }
    }
    public record LabCaseResult(long testcaseId, boolean passed, java.math.BigDecimal score, String actualOutput, String message) { }
}
