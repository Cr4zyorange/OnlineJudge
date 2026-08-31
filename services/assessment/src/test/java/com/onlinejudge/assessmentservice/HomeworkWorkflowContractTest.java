package com.onlinejudge.assessmentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
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
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_homework_submission");
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
                .andExpect(jsonPath("$.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        String submissionId = mapper.readTree(submitted).path("submissionId").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission WHERE id = ? AND source_type = 'HWK'", Integer.class, submissionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE submission_id = ? AND state = 'PENDING'", Integer.class, submissionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_final FROM assessment_homework_submission WHERE submission_id = ?", Boolean.class, submissionId)).isTrue();
    }

    @Test
    void workerCompletionPersistsHomeworkResultAndVersionedSourceGradeBeforePassiveQuery() throws Exception {
        String teacherId = "teacher-315-" + UUID.randomUUID();
        String studentId = "student-315-" + UUID.randomUUID();
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
        String submissionId = mapper.readTree(submitted).path("submissionId").asText();
        String taskId = mapper.readTree(submitted).path("taskId").asText();

        worker.runOne("homework-worker-315", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("80"), new BigDecimal("100")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.score").value(80));
        assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_homework_submission WHERE submission_id = ?", String.class, submissionId)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = ? AND student_id = ?", Long.class, Long.toString(homeworkId), studentId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "HWK:" + homeworkId + ":" + studentId)).isEqualTo(1);
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
        try {
            mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                            .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("HWK_5003"));
        } finally {
            jdbc.execute("ALTER TABLE assessment_event_outbox DROP CONSTRAINT force_homework_outbox_failure");
        }

        assertThat(jdbc.queryForObject("SELECT status FROM assessment_homework WHERE id = ?", String.class, homeworkId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE aggregate_id = ?", Integer.class, Long.toString(homeworkId))).isZero();
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
        String submissionId = mapper.readTree(submitted).path("submissionId").asText();
        String taskId = mapper.readTree(submitted).path("taskId").asText();
        worker.runOne("homework-worker-failure", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_UNCONFIGURED"));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.taskState").value("PENDING"));

        assertThat(jdbc.queryForObject("SELECT manual_replay_count FROM evaluation_task WHERE id = ?", Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade WHERE source_id = ?", Integer.class,
                Long.toString(homeworkId))).isZero();
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
