package com.onlinejudge.assessmentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/** Issue #315 acceptance through newly-created, run-scoped Homework facts. */
@SpringBootTest
@AutoConfigureMockMvc
class HomeworkWorkflowContractTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockBean CoursePermissionClient coursePermissions;
    @Autowired AssessmentWorker worker;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("homework-workflow-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_homework_review_log");
        jdbc.update("DELETE FROM assessment_homework_evaluation");
        jdbc.update("DELETE FROM assessment_source_grade_snapshot");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_homework_submission");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_homework_testcase");
        jdbc.update("DELETE FROM assessment_homework");
        jdbc.update("DELETE FROM assessment_course_member_projection");
    }

    @Test
    void teacherPublishesHomeworkAndCommitsCanonicalOutboxInTheSameLocalTransaction() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String title = "homework-315-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", teacherId);
        when(coursePermissions.canManageCourse("course-315", teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-315","title":"%s","description":"issue 315 durable homework",
                                 "type":"CODE","deadline":"%s","totalScore":100,"allowResubmit":true,
                                 "allowLateSubmit":false,"languages":["python"],
                                 "testCases":[{"input":"hello\\n","expectedOutput":"HELLO\\n","scoreWeight":100,"hidden":false,"sortOrder":1}]}
                                """.formatted(title, Instant.parse("2030-01-01T12:00:00Z"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(jdbc.queryForObject("SELECT status FROM assessment_homework WHERE id = ?", String.class, homeworkId))
                .isEqualTo("PUBLISHED");
        String envelope = jdbc.queryForObject("""
                SELECT payload_json FROM assessment_event_outbox
                 WHERE event_type = 'assessment.homework.published.v2' AND aggregate_id = ?
                """, String.class, Long.toString(homeworkId));
        var event = mapper.readTree(envelope);
        assertThat(event.path("payload").path("title").asText()).isEqualTo(title);
        assertThat(event.path("payload").path("receiverScope").asText()).isEqualTo("COURSE_ACTIVE_STUDENTS");
        assertThat(event.path("payload").has("recipientUserIds")).isFalse();
        assertThat(jdbc.queryForObject("SELECT state FROM assessment_event_outbox WHERE aggregate_id = ?", String.class,
                Long.toString(homeworkId))).isEqualTo("PENDING");
    }

    @Test
    void studentSubmissionPersistsHomeworkVersionAndDurableTaskInOneTransaction() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "submission-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));

        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print(input().upper())\",\"language\":\"python\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn().getResponse().getContentAsString();

        String submissionId = privateSubmissionId(submitted);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission WHERE id = ? AND source_type = 'HWK'", Integer.class, submissionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE submission_id = ? AND state = 'PENDING'", Integer.class, submissionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_final FROM assessment_homework_submission WHERE submission_id = ?", Boolean.class, submissionId)).isTrue();
    }

    @Test
    void frontendCodeTextSubmissionNeedsNoRequestIdAndReceivesTheStandardSuccessEnvelope() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "frontend-submit-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));

        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeText\":\"print('frontend')\",\"language\":\"python\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.submissionId").isNumber())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long publicSubmissionId = publicSubmissionId(submitted);
        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", publicSubmissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submissionId").value(publicSubmissionId))
                .andExpect(jsonPath("$.data.taskState").value("PENDING"));
    }

    @Test
    void workerCompletionDefersHomeworkSourceGradeUntilTeacherPublishesScores() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        long homeworkId = createAndPublishCodeHomework(teacherId, "result-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print(input().upper())\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = privateSubmissionId(submitted);
        String taskId = taskIdForSubmission(submissionId);

        worker.runOne("homework-worker-315", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("80"), new BigDecimal("100")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(80));
        assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_homework_submission WHERE submission_id = ?", String.class, submissionId)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?", Integer.class, Long.toString(homeworkId), studentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "HWK:" + homeworkId + ":" + studentId)).isZero();

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORE_PUBLISHED"));
        assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?", Long.class, Long.toString(homeworkId), studentId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "HWK:" + homeworkId + ":" + studentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_review_log WHERE submission_id = ? AND operation_type = 'SCORE_PUBLISHED' AND operator_id = ? AND new_score = ?",
                Integer.class, submissionId, teacherId, new BigDecimal("80"))).isEqualTo(1);
    }

    @Test
    void scorePublicationRejectsPendingFinalSubmissionUntilItsWorkerCompletes() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        long homeworkId = createAndPublishCodeHomework(teacherId, "publish-fence-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String submissionId = submitCodeHomework(homeworkId, studentToken, "print('pending')");

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT status FROM assessment_homework WHERE id = ?", String.class, homeworkId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT state FROM evaluation_task WHERE submission_id = ?", String.class, submissionId))
                .isEqualTo("PENDING");
        assertNoSourceGrade(homeworkId, studentId);

        worker.runOne("homework-worker-publish-fence", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("86"), new BigDecimal("100")));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORE_PUBLISHED"));
        assertCurrentSourceGrade(homeworkId, studentId, "SCORED", new BigDecimal("86"), 1);
    }

    @Test
    void localOutboxWriteFailureRollsPublicationBackAndReturnsHwk5003() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", teacherId);
        when(coursePermissions.canManageCourse("course-315", teacherId)).thenReturn(true);
        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-315","title":"rollback-315","description":"local outbox failure",
                                 "type":"CODE","deadline":"2030-01-01T12:00:00Z","totalScore":100,
                                 "allowResubmit":true,"allowLateSubmit":false,"languages":["python"],
                                 "testCases":[{"input":"x","expectedOutput":"x","scoreWeight":100,"hidden":false,"sortOrder":1}]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();
        jdbc.execute("ALTER TABLE assessment_event_outbox ADD CONSTRAINT force_homework_outbox_failure CHECK (event_type <> 'assessment.homework.published.v2')");
        String requestId = UUID.randomUUID().toString();
        try {
            mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                            .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", requestId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("HWK_5003"))
                    .andExpect(jsonPath("$.retryable").value(true))
                    .andExpect(jsonPath("$.requestId").value(requestId));
        } finally {
            jdbc.execute("ALTER TABLE assessment_event_outbox DROP CONSTRAINT force_homework_outbox_failure");
        }

        assertThat(jdbc.queryForObject("SELECT status FROM assessment_homework WHERE id = ?", String.class, homeworkId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE aggregate_id = ?", Integer.class, Long.toString(homeworkId))).isZero();
    }

    @Test
    void unavailableCourseAuthorizationReturns503AndWritesNoBusinessFacts() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        long homeworkId = createAndPublishCodeHomework(teacherId, "auth-down-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        when(coursePermissions.canManageCourse("course-315", teacherId))
                .thenThrow(new CourseAuthorizationUnavailableException("course authorization is unavailable"));
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", requestId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COURSE_AUTHORIZATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.requestId").value(requestId));
        assertThat(jdbc.queryForObject("SELECT status FROM assessment_homework WHERE id = ?", String.class, homeworkId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox", Integer.class)).isEqualTo(1);
    }

    @Test
    void managerWriteRejectionsUseTheCanonicalErrorEnvelope() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "envelope-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", requestId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4003"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void ownerCanReadOwnEvaluationWhenCourseAuthorizationIsUnavailable() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "owner-read-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('result')\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = privateSubmissionId(submitted);
        worker.runOne("homework-worker-owner-read", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("80"), new BigDecimal("100")));
        when(coursePermissions.canManageCourse("course-315", studentId))
                .thenThrow(new CourseAuthorizationUnavailableException("course authorization is unavailable"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(80));
    }

    @Test
    void deadlineAndCourseMembershipRejectionsCreateExactlyNoSubmissionFacts() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "closed-315-" + UUID.randomUUID(), false, false,
                Instant.parse("2020-01-01T00:00:00Z"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('late')\",\"language\":\"python\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isZero();

        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('late')\",\"language\":\"python\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isZero();
    }

    @Test
    void authorizedTeacherCanReplayOnlyATerminalFailedHomeworkEvaluation() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "replay-315-" + UUID.randomUUID(), true, false,
                Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('failure')\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = privateSubmissionId(submitted);
        String taskId = taskIdForSubmission(submissionId);
        worker.runOne("homework-worker-failure", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNCONFIGURED"));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"rerun after sandbox configuration repair\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.taskState").value("PENDING"));

        assertThat(jdbc.queryForObject("SELECT manual_replay_count FROM evaluation_task WHERE id = ?", Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade WHERE source_id = ?", Integer.class,
                Long.toString(homeworkId))).isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM assessment_homework_review_log WHERE submission_id = ? AND operation_type = 'REJUDGE'",
                String.class, submissionId)).isEqualTo("rerun after sandbox configuration repair");
    }

    @ParameterizedTest(name = "teacher can replay successful homework result {0}")
    @ValueSource(strings = {"ACCEPTED", "WRONG_ANSWER"})
    void authorizedTeacherCanReplaySuccessfulHomeworkEvaluation(String evaluationStatus) throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "successful-replay-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('result')\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = privateSubmissionId(submitted);
        String taskId = taskIdForSubmission(submissionId);
        assertThat(worker.runOne("homework-worker-success-" + evaluationStatus, task -> new AssessmentWorker.EvaluationOutcome(
                true, evaluationStatus, "ACCEPTED".equals(evaluationStatus) ? new BigDecimal("100") : BigDecimal.ZERO,
                new BigDecimal("100"))))
                .hasValueSatisfying(completed -> {
                    assertThat(completed.state().name()).isEqualTo("SUCCEEDED");
                    assertThat(completed.resultStatus()).isEqualTo(evaluationStatus);
                });

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"teacher requested rejudge\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.taskState").value("PENDING"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("PENDING"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.score").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.finalScore").value(org.hamcrest.Matchers.nullValue()));

        assertThat(jdbc.queryForObject("SELECT manual_replay_count FROM evaluation_task WHERE id = ?", Integer.class, taskId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_submission WHERE id = ?", String.class,
                submissionId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_homework_submission WHERE submission_id = ?",
                String.class, submissionId)).isEqualTo("PENDING");
    }

    @Test
    void reevaluationAppendsAnImmutableEvaluationRecordAndARejudgeAuditEntry() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "evaluation-history-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('history')\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = privateSubmissionId(submitted);
        worker.runOne("homework-worker-evaluation-history", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("95"), new BigDecimal("100")));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"rerun evidence collection\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_evaluation WHERE submission_id = ? AND status = 'ACCEPTED'", Integer.class, submissionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_review_log WHERE submission_id = ? AND operation_type = 'REJUDGE'", Integer.class, submissionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM assessment_homework_review_log WHERE submission_id = ? AND operation_type = 'REJUDGE'", String.class, submissionId))
                .isEqualTo("rerun evidence collection");
        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationHistory[0].status").value("ACCEPTED"));
    }

    @Test
    void reevaluationBeforeScorePublicationDoesNotExposeASourceGrade() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "ungraded-replay-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('result')\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = privateSubmissionId(submitted);
        worker.runOne("homework-worker-first-success", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("88"), new BigDecimal("100")));

        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"rerun before publication\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("PENDING"));

        assertNoSourceGrade(homeworkId, studentId);

        worker.runOne("homework-worker-replay-failure", task -> AssessmentWorker.EvaluationOutcome.failed("COMPILE_ERROR"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("FAILED"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.data.score").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.finalScore").value(org.hamcrest.Matchers.nullValue()));
        assertNoSourceGrade(homeworkId, studentId);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_event_outbox
                 WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?
                """, Integer.class, "HWK:" + homeworkId + ":" + studentId)).isZero();
    }

    @Test
    void historicalHomeworkTasksCannotChangeTheCurrentSubmissionSourceGrade() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "current-submission-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));

        String firstSubmissionId = submitCodeHomework(homeworkId, studentToken, "print('first')");
        worker.runOne("homework-worker-first-current", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("80"), new BigDecimal("100")));

        String currentSubmissionId = submitCodeHomework(homeworkId, studentToken, "print('current')");
        assertNoSourceGrade(homeworkId, studentId);
        worker.runOne("homework-worker-current", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("90"), new BigDecimal("100")));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        assertCurrentSourceGrade(homeworkId, studentId, "SCORED", new BigDecimal("90"), 1);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", firstSubmissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"must not reopen published homework\"}"))
                .andExpect(status().isConflict());

        assertCurrentSourceGrade(homeworkId, studentId, "SCORED", new BigDecimal("90"), 1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_event_outbox
                 WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?
                """, Integer.class, "HWK:" + homeworkId + ":" + studentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_final FROM assessment_homework_submission WHERE submission_id = ?",
                Boolean.class, currentSubmissionId)).isTrue();
    }

    @Test
    void publishedHomeworkRejectsCurrentSubmissionReevaluationWithoutRevokingItsGrade() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "published-rejudge-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String submissionId = submitCodeHomework(homeworkId, studentToken, "print('published')");
        worker.runOne("homework-worker-published-rejudge", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("91"), new BigDecimal("100")));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"post-publication correction\"}"))
                .andExpect(status().isConflict());

        assertCurrentSourceGrade(homeworkId, studentId, "SCORED", new BigDecimal("91"), 1);
        assertThat(jdbc.queryForObject("SELECT state FROM evaluation_task WHERE submission_id = ?", String.class, submissionId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_review_log WHERE submission_id = ? AND operation_type = 'REJUDGE'", Integer.class, submissionId))
                .isZero();
    }

    @Test
    void homeworkReevaluationEndpointRejectsLabTask() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        when(coursePermissions.canManageCourse("course-315", teacherId)).thenReturn(true);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String submitted = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"LAB\",\"sourceId\":\"lab-315\",\"courseId\":\"course-315\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String submissionId = mapper.readTree(submitted).path("submissionId").asText();
        String taskId = mapper.readTree(submitted).path("taskId").asText();
        worker.runOne("lab-worker-failure", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNCONFIGURED"));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lab route must remain unavailable\"}"))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT state FROM evaluation_task WHERE id = ?", String.class, taskId))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT manual_replay_count FROM evaluation_task WHERE id = ?", Integer.class, taskId))
                .isZero();
    }

    private void assertSourceGradeIsUngraded(long homeworkId, String studentId) throws Exception {
        mockMvc.perform(get("/internal/v2/source-grades")
                        .param("courseId", "course-315")
                        .param("sourceType", "HWK")
                        .param("sourceId", Long.toString(homeworkId))
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .header("X-OnlineJudge-Service-Authorization", "Bearer "
                                + TestJwtFactory.serviceToken(KEY, "homework-workflow-kid", "assessment",
                                List.of("grades:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].studentId").value(studentId))
                .andExpect(jsonPath("$.items[0].status").value("UNGRADED"))
                .andExpect(jsonPath("$.items[0].score").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].sourceVersion").value(2));
    }

    private void assertNoSourceGrade(long homeworkId, String studentId) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_source_grade
                 WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?
                """, Integer.class, Long.toString(homeworkId), studentId)).isZero();
    }

    private String submitCodeHomework(long homeworkId, String studentToken, String code) throws Exception {
        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"language\":\"python\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return privateSubmissionId(submitted);
    }

    private String privateSubmissionId(String response) throws Exception {
        long publicSubmissionId = publicSubmissionId(response);
        return jdbc.queryForObject("SELECT submission_id FROM assessment_homework_submission WHERE public_id = ?",
                String.class, publicSubmissionId);
    }

    private long publicSubmissionId(String response) throws Exception {
        return mapper.readTree(response).path("data").path("submissionId").asLong();
    }

    private String taskIdForSubmission(String submissionId) {
        return jdbc.queryForObject("SELECT id FROM evaluation_task WHERE submission_id = ?", String.class, submissionId);
    }

    private void assertCurrentSourceGrade(long homeworkId, String studentId, String expectedStatus,
                                          BigDecimal expectedScore, int expectedVersion) {
        assertThat(jdbc.queryForObject("SELECT status FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?",
                String.class, Long.toString(homeworkId), studentId)).isEqualTo(expectedStatus);
        BigDecimal actualScore = jdbc.queryForObject("SELECT score FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?",
                BigDecimal.class, Long.toString(homeworkId), studentId);
        if (expectedScore == null) assertThat(actualScore).isNull();
        else assertThat(actualScore).isEqualByComparingTo(expectedScore);
        assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?",
                Long.class, Long.toString(homeworkId), studentId)).isEqualTo((long) expectedVersion);
    }

    @Test
    void homeworkTablesRejectOrphanTestcasesAndBusinessSubmissions() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO assessment_homework_testcase
                    (homework_id, input_text, expected_output, score_weight, is_hidden, sort_order)
                VALUES (315404, 'input', 'output', 100, TRUE, 1)
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO assessment_homework_submission
                    (submission_id, homework_id, student_id, submission_version, language, submit_status,
                     evaluation_status, is_final, submitted_at)
                VALUES ('orphan-submission-315', 315404, 'student-315', 1, 'python', 'SUBMITTED',
                        'PENDING', TRUE, CURRENT_TIMESTAMP)
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private long createAndPublishCodeHomework(String teacherId, String title, boolean allowResubmit,
                                              boolean allowLateSubmit, Instant deadline) throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", teacherId);
        when(coursePermissions.canManageCourse("course-315", teacherId)).thenReturn(true);
        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-315","title":"%s","description":"issue 315 durable homework",
                                 "type":"CODE","deadline":"%s","totalScore":100,"allowResubmit":%s,
                                 "allowLateSubmit":%s,"languages":["python"],
                                 "testCases":[{"input":"hello\\n","expectedOutput":"HELLO\\n","scoreWeight":100,"hidden":false,"sortOrder":1}]}
                                """.formatted(title, deadline, allowResubmit, allowLateSubmit)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        return homeworkId;
    }
}
