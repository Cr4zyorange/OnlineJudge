package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.model.TaskState;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Retryable sandbox failures wait for a bounded re-claim; only an explicit replay revives a terminal audit row. */
@SpringBootTest(properties = {
        "assessment.worker.max-attempts=2",
        "assessment.worker.retry-backoff=PT0S"
})
class WorkerRetryContractTest {
    @Autowired AssessmentSubmissionService submissions;
    @Autowired AssessmentWorker worker;
    @Autowired EvaluationTaskRepository tasks;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
    }

    @Test
    void retryableTimeoutStopsAtTheBoundAndTeacherReplayFencesTheOldGeneration() {
        var submitted = submissions.submit(new AssessmentSubmissionService.SubmissionCommand("HWK", "retry-homework", "course-retry", "student-retry", "persistent://retry"));

        var first = worker.runOne("worker-first", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_TIMEOUT")).orElseThrow();
        assertThat(first.state()).isEqualTo(TaskState.RETRY_WAIT);
        assertThat(first.attempt()).isEqualTo(1);

        var terminal = worker.runOne("worker-second", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_TIMEOUT")).orElseThrow();
        assertThat(terminal.state()).isEqualTo(TaskState.FAILED);
        assertThat(terminal.attempt()).isEqualTo(2);

        assertThat(tasks.manualReplay(submitted.taskId(), "teacher-retry", java.time.Instant.now())).isTrue();
        var replayed = tasks.find(submitted.taskId()).orElseThrow();
        assertThat(replayed.state()).isEqualTo(TaskState.PENDING);
        assertThat(tasks.complete(submitted.taskId(), "worker-second", terminal.generation(), true, "STALE", java.time.Instant.now())).isFalse();

        var succeeded = worker.runOne("worker-replay", task -> AssessmentWorker.EvaluationOutcome.successful("ACCEPTED")).orElseThrow();
        assertThat(succeeded.state()).isEqualTo(TaskState.SUCCEEDED);
        assertThat(succeeded.generation()).isGreaterThan(terminal.generation());
        assertThat(jdbc.queryForObject("SELECT manual_replay_count FROM evaluation_task WHERE id=?", Integer.class, submitted.taskId())).isEqualTo(1);
    }
}
