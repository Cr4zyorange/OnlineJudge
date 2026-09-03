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
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
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
    @Autowired EvaluationTaskRepository tasks;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("homework-workflow-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_course_projection_gap");
        jdbc.update("DELETE FROM assessment_homework_review_log");
        jdbc.update("DELETE FROM assessment_homework_evaluation");
        jdbc.update("DELETE FROM assessment_source_grade_snapshot");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_homework_attachment");
        jdbc.update("DELETE FROM assessment_homework_submission");
        jdbc.update("DELETE FROM assessment_homework_question");
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
    void objectiveHomeworkPreservesTheTeacherKeyAndScoresTheStudentAnswerWithoutLeakingIt() throws Exception {
        String teacherId = "teacher-objective-" + UUID.randomUUID();
        String studentId = "student-objective-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('9501', ?, 'ACTIVE', 1)", teacherId);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('9501', ?, 'ACTIVE', 1)", studentId);
        when(coursePermissions.canManageCourse("9501", teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":9501,"title":"objective browser contract","description":"browser payload",
                                 "type":"OBJECTIVE","deadline":"2030-01-01T12:00:00Z","totalScore":100,"allowResubmit":true,
                                 "allowLateSubmit":false,"questions":[{"questionType":"SINGLE_CHOICE","stem":"1 + 1 = ?","optionsJson":"[\\"1\\",\\"2\\"]","answerJson":"[\\"2\\"]","score":100,"sortOrder":1}],"testCases":[]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", homeworkId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].answerJson").doesNotExist());
        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answerJson\":\"{\\\"q1\\\":[\\\"2\\\"]}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submitType").value("OBJECTIVE"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.autoScore").value(100));
    }

    @Test
    void homeworkStatisticsDenyStudentsWithFrozenCodeAndAggregateTheActiveRosterForManagers() throws Exception {
        String courseId = "course-statistics-320";
        String teacherId = "320101";
        String submittedStudentId = "320102";
        String unsubmittedStudentId = "320103";
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", submittedStudentId, List.of("STUDENT"));
        String nonManagingTeacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", "320104", List.of("TEACHER"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES (?, ?, 'ACTIVE', 1)", courseId, teacherId);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES (?, ?, 'ACTIVE', 1)", courseId, submittedStudentId);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES (?, ?, 'ACTIVE', 1)", courseId, unsubmittedStudentId);
        when(coursePermissions.canManageCourse(courseId, teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"%s","title":"statistics contract","description":"active roster aggregate",
                                 "type":"OBJECTIVE","deadline":"2030-01-01T12:00:00Z","totalScore":100,"allowResubmit":true,
                                 "allowLateSubmit":false,"questions":[{"questionType":"SINGLE_CHOICE","stem":"1 + 1 = ?","optionsJson":"[\\"1\\",\\"2\\"]","answerJson":"[\\"2\\"]","score":100,"sortOrder":1}],"testCases":[]}
                                """.formatted(courseId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerJson\":\"{\\\"q1\\\":[\\\"2\\\"]}\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .header("Authorization", "Bearer " + nonManagingTeacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .param("page", "1").param("size", "1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.totalStudentCount").value(2))
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath("$.data.unsubmittedCount").value(1))
                .andExpect(jsonPath("$.data.autoEvaluableCount").value(1))
                .andExpect(jsonPath("$.data.pendingEvaluationCount").value(0))
                .andExpect(jsonPath("$.data.evaluatedCount").value(1))
                .andExpect(jsonPath("$.data.pendingReviewCount").value(0))
                .andExpect(jsonPath("$.data.reviewedCount").value(1))
                .andExpect(jsonPath("$.data.scoredCount").value(1))
                .andExpect(jsonPath("$.data.averageScore").value(100))
                .andExpect(jsonPath("$.data.maxScore").value(100))
                .andExpect(jsonPath("$.data.minScore").value(100))
                .andExpect(jsonPath("$.data.scoreDistribution['90-100']").value(1))
                .andExpect(jsonPath("$.data.unsubmittedPage").value(1))
                .andExpect(jsonPath("$.data.unsubmittedSize").value(1))
                .andExpect(jsonPath("$.data.unsubmittedTotal").value(1))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds[0]").value(320103));
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .param("page", Integer.toString(Integer.MAX_VALUE)).param("size", "100")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unsubmittedPage").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.unsubmittedSize").value(100))
                .andExpect(jsonPath("$.data.unsubmittedTotal").value(1))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds").isEmpty());
    }

    @Test
    void codeHomeworkAcceptsTheFrontendLanguageAndTestcasePayload() throws Exception {
        String teacherId = "teacher-code-browser-" + UUID.randomUUID();
        String studentId = "student-code-browser-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('9501', ?, 'ACTIVE', 1)", teacherId);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('9501', ?, 'ACTIVE', 1)", studentId);
        when(coursePermissions.canManageCourse("9501", teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":9501,"title":"code browser contract","description":"browser payload",
                                 "type":"CODE","deadline":"2030-01-01T12:00:00Z","totalScore":100,"allowResubmit":true,
                                 "allowLateSubmit":false,"languageLimitJson":"[\\"python\\"]",
                                 "testCases":[{"inputData":"1 2","expectedOutput":"3","scoreWeight":100,"hidden":false,"sortOrder":1}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codeText\":\"print(3)\",\"language\":\"python\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submitType").value("CODE"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"));
    }

    @Test
    void fileHomeworkRejectsDisguisedPdfThenBindsTheStudentOwnedTextAttachment() throws Exception {
        String teacherId = "teacher-file-browser-" + UUID.randomUUID();
        String studentId = "student-file-browser-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('9501', ?, 'ACTIVE', 1)", teacherId);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('9501', ?, 'ACTIVE', 1)", studentId);
        when(coursePermissions.canManageCourse("9501", teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":9501,"title":"file browser contract","description":"browser payload",
                                 "type":"FILE","deadline":"2030-01-01T12:00:00Z","totalScore":100,"allowResubmit":true,
                                 "allowLateSubmit":false,"questions":[],"testCases":[]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/homeworks/{homeworkId}/attachments", homeworkId)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "disguised-answer.pdf", "application/pdf",
                                "not-a-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + studentToken)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        String uploaded = mockMvc.perform(multipart("/api/v1/homeworks/{homeworkId}/attachments", homeworkId)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "answer.txt", "text/plain",
                                "durable file answer".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + studentToken)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.uploadedAt").exists())
                .andReturn().getResponse().getContentAsString();
        String fileId = mapper.readTree(uploaded).path("data").path("fileId").asText();

        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fileIds\":[\"" + fileId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submitType").value("FILE"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("NONE"))
                .andReturn().getResponse().getContentAsString();
        long publicSubmissionId = publicSubmissionId(submitted);
        assertThat(publicSubmissionId).isPositive();
        assertThat(jdbc.queryForObject("SELECT status FROM assessment_homework_attachment WHERE file_id = ?", String.class, fileId))
                .isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT submission_id IS NOT NULL FROM assessment_homework_attachment WHERE file_id = ?", Boolean.class, fileId))
                .isTrue();
    }

    @Test
    void gatewayCompactRequestIdIsCanonicalizedForHomeworkOutboxCorrelation() throws Exception {
        String teacherId = "teacher-gateway-" + UUID.randomUUID();
        String title = "homework-gateway-" + UUID.randomUUID();
        UUID gatewayRequestUuid = UUID.randomUUID();
        String compactGatewayRequestId = gatewayRequestUuid.toString().replace("-", "");
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", teacherId);
        when(coursePermissions.canManageCourse("course-315", teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", compactGatewayRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-315","title":"%s","description":"gateway request id",
                                 "type":"CODE","deadline":"%s","totalScore":100,"allowResubmit":true,
                                 "allowLateSubmit":false,"languages":["python"],
                                 "testCases":[{"input":"hello\\n","expectedOutput":"HELLO\\n","scoreWeight":100,"hidden":false,"sortOrder":1}]}
                                """.formatted(title, Instant.parse("2030-01-01T12:00:00Z"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", compactGatewayRequestId))
                .andExpect(status().isOk());

        String correlationId = jdbc.queryForObject("""
                SELECT correlation_id FROM assessment_event_outbox
                 WHERE event_type = 'assessment.homework.published.v2' AND aggregate_id = ?
                """, String.class, Long.toString(homeworkId));
        assertThat(correlationId).isEqualTo(gatewayRequestUuid.toString());
        String payload = jdbc.queryForObject("""
                SELECT payload_json FROM assessment_event_outbox
                 WHERE event_type = 'assessment.homework.published.v2' AND aggregate_id = ?
                """, String.class, Long.toString(homeworkId));
        assertThat(mapper.readTree(payload).path("correlationId").asText())
                .isEqualTo(gatewayRequestUuid.toString());
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
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.submissionId").isNumber())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(submitted).path("code").isTextual()).isTrue();
        assertThat(mapper.readTree(submitted).path("code").asText()).isEqualTo("0");
        long publicSubmissionId = publicSubmissionId(submitted);
        String evaluation = mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", publicSubmissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submissionId").value(publicSubmissionId))
                .andExpect(jsonPath("$.data.taskState").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(evaluation).path("code").isTextual()).isTrue();
        assertThat(mapper.readTree(evaluation).path("code").asText()).isEqualTo("0");
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
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(homeworkId))
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));
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
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(homeworkId))
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));
        assertCurrentSourceGrade(homeworkId, studentId, "SCORED", new BigDecimal("86"), 1);
    }

    @Test
    void courseManagerWhoOwnsTheSubmissionCanSeeItsUnpublishedEvaluation() throws Exception {
        String managerId = "manager-student-315-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(managerId, "manager-result-315-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        String managerToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", managerId,
                List.of("TEACHER", "STUDENT"));
        String submissionId = submitCodeHomework(homeworkId, managerToken, "print('manager result')");
        worker.runOne("homework-worker-manager-result-315", task -> new AssessmentWorker.EvaluationOutcome(
                true, "ACCEPTED", new BigDecimal("80"), new BigDecimal("100")));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(80))
                .andExpect(jsonPath("$.data.finalScore").value(80));
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
    void courseProjectionGapFallsBackToCourseAuthorizationAndWritesNothingWhenDenied() throws Exception {
        String teacherId = "teacher-356-" + UUID.randomUUID();
        String studentId = "student-356-" + UUID.randomUUID();
        long homeworkId = createAndPublishCodeHomework(teacherId, "projection-gap-356-" + UUID.randomUUID(), true,
                false, Instant.parse("2030-01-01T12:00:00Z"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        jdbc.update("INSERT INTO assessment_course_projection_gap (course_id, user_id, expected_version, observed_version) VALUES ('course-315', ?, 1, 3)", studentId);
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        String requestId = UUID.randomUUID().toString();
        when(coursePermissions.canViewCourse("course-315", studentId, requestId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print('gap')\",\"language\":\"python\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4003"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.requestId").value(requestId));

        verify(coursePermissions).canViewCourse("course-315", studentId, requestId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isZero();
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4004"));
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
                .andExpect(jsonPath("$.data.score").value(0))
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
        String submissionId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        jdbc.update("""
                INSERT INTO assessment_submission
                    (id, source_type, source_id, course_id, student_id, content_ref, evaluation_status, created_at)
                VALUES (?, 'LAB', 'lab-315', 'course-315', ?, 'fixture://lab-315', 'PENDING', ?)
                """, submissionId, studentId, java.sql.Timestamp.from(createdAt));
        tasks.insert(taskId, submissionId, "LAB", "lab-315", "course-315", studentId, "lab-fixture-315", createdAt);
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

    @Test
    void textHomeworkCompletesManualReviewAndScorePublicationWithoutCreatingACodeTask() throws Exception {
        String teacherId = "teacher-text-" + UUID.randomUUID();
        String studentId = "student-text-" + UUID.randomUUID();
        String teacherToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", teacherId, List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "homework-workflow-kid", studentId, List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", teacherId);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-315', ?, 'ACTIVE', 1)", studentId);
        when(coursePermissions.canManageCourse("course-315", teacherId)).thenReturn(true);

        String created = mockMvc.perform(post("/api/v1/homeworks")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-315","title":"text homework","description":"manual review",
                                 "type":"TEXT","deadline":"2030-01-01T12:00:00Z","totalScore":100,
                                 "allowResubmit":true,"allowLateSubmit":false,"questions":[],"testCases":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andReturn().getResponse().getContentAsString();
        long homeworkId = mapper.readTree(created).path("id").asLong();

        mockMvc.perform(get("/api/v1/homeworks")
                        .param("courseId", "course-315")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(homeworkId))
                .andExpect(jsonPath("$.data.list[0].type").value("TEXT"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        String submitted = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerText\":\"a durable text answer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submitType").value("TEXT"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("NONE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("UNREVIEWED"))
                .andReturn().getResponse().getContentAsString();
        long publicSubmissionId = publicSubmissionId(submitted);

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/my-submissions", homeworkId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].submissionId").value(publicSubmissionId))
                .andExpect(jsonPath("$.data[0].answerText").value("a durable text answer"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].submissionId").value(publicSubmissionId));

        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", publicSubmissionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manualScore\":88,\"finalScore\":88,\"comment\":\"reviewed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(88))
                .andExpect(jsonPath("$.data.comment").value("reviewed"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));
        assertCurrentSourceGrade(homeworkId, studentId, "SCORED", new BigDecimal("88"), 1);

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", publicSubmissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerText").value("a durable text answer"))
                .andExpect(jsonPath("$.data.finalScore").value(88))
                .andExpect(jsonPath("$.data.comment").value("reviewed"));
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
