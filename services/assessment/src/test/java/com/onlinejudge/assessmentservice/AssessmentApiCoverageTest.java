package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #367 API coverage: the Assessment readiness probe and the evaluation
 * replay boundary get dedicated HTTP contract tests.
 */
@SpringBootTest(properties = "assessment.worker.enabled=false")
@AutoConfigureMockMvc
class AssessmentApiCoverageTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AssessmentSubmissionService submissions;

    @Autowired
    private EvaluationTaskRepository tasks;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("assessment-coverage-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_homework_review_log");
        jdbc.update("DELETE FROM assessment_homework_evaluation");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_homework_submission");
        jdbc.update("DELETE FROM assessment_lab_evaluation_result");
        jdbc.update("DELETE FROM assessment_lab_testcase");
        jdbc.update("DELETE FROM assessment_lab_score_change_log");
        jdbc.update("DELETE FROM assessment_lab_score");
        jdbc.update("DELETE FROM assessment_lab_submission");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_lab_experiment");
        jdbc.update("DELETE FROM assessment_course_member_projection");
    }

    @Test
    void readinessProbeReportsUpWhenDatabaseAnswers() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void evaluationReplayRequiresRequestIdAndActiveTeacherMembership() throws Exception {
        var submitted = submissions.submit(new AssessmentSubmissionService.SubmissionCommand(
                "HWK", "homework-replay", "course-7", "student-42", "stored://submission-replay"));
        String taskId = submitted.taskId();
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-7', 'teacher-7', 'ACTIVE', 1)");
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-7', 'outside-teacher', 'REMOVED', 1)");

        mockMvc.perform(post("/api/v1/evaluations/{taskId}/replay", taskId)
                        .header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "assessment-coverage-kid", "student-42", List.of("STUDENT"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/evaluations/{taskId}/replay", taskId)
                        .header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "assessment-coverage-kid", "student-42", List.of("STUDENT")))
                        .header("X-Request-Id", "replay-student-1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/evaluations/{taskId}/replay", taskId)
                        .header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "assessment-coverage-kid", "outside-teacher", List.of("TEACHER")))
                        .header("X-Request-Id", "replay-nonmember-1"))
                .andExpect(status().isForbidden());

        // A PENDING task is not terminal, so the durable replay guard returns 409.
        mockMvc.perform(post("/api/v1/evaluations/{taskId}/replay", taskId)
                        .header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "assessment-coverage-kid", "teacher-7", List.of("TEACHER")))
                        .header("X-Request-Id", "replay-teacher-1"))
                .andExpect(status().isConflict());

        // Replay is passive: the task state stays untouched.
        org.assertj.core.api.Assertions.assertThat(tasks.find(taskId).orElseThrow().state().name()).isEqualTo("PENDING");
    }
}
