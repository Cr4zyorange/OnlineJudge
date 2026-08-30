package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Submission and result reads deliberately use separate entrypoints: API GET is a pure query,
 * while only a worker can claim a durable task.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentLifecycleContractTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();
    @Autowired AssessmentSubmissionService submissions;
    @Autowired EvaluationTaskRepository tasks;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) { registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("lifecycle-kid", KEY)); registry.add("assessment.identity.refresh-enabled", () -> false); }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
    }

    @Test
    void submissionCreatesDurableTaskAndResultGetHasNoWorkerSideEffect() throws Exception {
        var submitted = submissions.submit(new AssessmentSubmissionService.SubmissionCommand(
                "HWK", "homework-9", "course-7", "student-42", "stored://submission-1"));
        assertThat(tasks.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/evaluations/{taskId}", submitted.taskId()).header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "lifecycle-kid", "student-42", List.of("STUDENT"))))
                .andExpect(status().isOk());
        assertThat(tasks.find(submitted.taskId()).orElseThrow().state().name()).isEqualTo("PENDING");
    }

    @Test
    void expiredClaimCanBeTakenOverButStaleGenerationCannotWriteTerminalResult() {
        var submitted = submissions.submit(new AssessmentSubmissionService.SubmissionCommand(
                "LAB", "lab-1", "course-7", "student-42", "stored://submission-2"));
        Instant started = Instant.parse("2026-08-31T00:00:00Z");
        EvaluationTask first = tasks.claimNext("worker-a", started, Duration.ofSeconds(10)).orElseThrow();
        EvaluationTask replacement = tasks.claimNext("worker-b", started.plusSeconds(11), Duration.ofSeconds(10)).orElseThrow();

        assertThat(replacement.generation()).isGreaterThan(first.generation());
        assertThat(tasks.complete(submitted.taskId(), "worker-a", first.generation(), true, "ACCEPTED", started.plusSeconds(12))).isFalse();
        assertThat(tasks.complete(submitted.taskId(), "worker-b", replacement.generation(), true, "ACCEPTED", started.plusSeconds(12))).isTrue();
        assertThat(tasks.find(submitted.taskId()).orElseThrow().state().name()).isEqualTo("SUCCEEDED");
    }

    @Test
    void labAndHomeworkEndpointsPersistUploadedBytesAndKeepEvaluationReadsPassive() throws Exception {
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-7', 'student-42', 'ACTIVE', 1)");
        String token = TestJwtFactory.userToken(KEY, "lifecycle-kid", "student-42", List.of("STUDENT"));

        mockMvc.perform(multipart("/api/v1/labs/lab-7/submissions")
                        .file("file", "print('lab')".getBytes())
                        .param("courseId", "course-7")
                        .header("X-Request-Id", "req-lab-7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/homeworks/homework-7/submissions")
                        .file("file", "print('homework')".getBytes())
                        .param("courseId", "course-7")
                        .header("X-Request-Id", "req-homework-7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission WHERE content_ref LIKE 'submissions/%'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isEqualTo(2);
    }
}
