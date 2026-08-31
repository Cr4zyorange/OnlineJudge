package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.model.TaskState;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentWorkerHeartbeatTest {
    @Test
    void runningSandboxIsHeartbeatedBeforeItsLeaseCanExpire() {
        var tasks = new RecordingTasks();
        var worker = new AssessmentWorker(tasks, null, null, new RecordingCompletion(tasks), Clock.systemUTC(), Duration.ofMillis(250), Duration.ofMillis(25));

        worker.runOne("worker-a", ignored -> {
            Thread.sleep(150);
            return AssessmentWorker.EvaluationOutcome.successful("ACCEPTED");
        });

        assertThat(tasks.heartbeats.get()).isGreaterThan(0);
    }

    private static final class RecordingTasks extends EvaluationTaskRepository {
        private final AtomicInteger heartbeats = new AtomicInteger();
        private final EvaluationTask task = new EvaluationTask("task-1", "submission-1", "LAB", "lab-1", "course-1", "student-1", TaskState.RUNNING, 1, "worker-a", Instant.now().plusSeconds(30), 1, null, "request-heartbeat-1");
        private RecordingTasks() { super(null); }
        @Override public Optional<EvaluationTask> claimNext(String workerId, Instant now, Duration lease) { return Optional.of(task); }
        @Override public boolean heartbeat(String id, String workerId, long generation, Instant now, Duration lease) { heartbeats.incrementAndGet(); return true; }
        @Override public Optional<EvaluationTask> find(String id) { return Optional.of(task); }
    }

    private static final class RecordingCompletion extends WorkerCompletionService {
        private RecordingCompletion(EvaluationTaskRepository tasks) { super(tasks, null, null); }
        @Override void complete(EvaluationTask task, String workerId, AssessmentWorker.EvaluationOutcome outcome, Instant finished) { }
    }
}
