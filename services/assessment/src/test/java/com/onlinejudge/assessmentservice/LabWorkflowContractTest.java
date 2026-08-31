package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;
import com.onlinejudge.assessmentservice.worker.SandboxEvaluator;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Issue #314 starts with the durable LAB aggregate rather than treating a generic
 * Assessment submission as an experiment.  Each test creates its own course/lab
 * facts so a later worker/outbox assertion can identify this run's event exactly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LabWorkflowContractTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssessmentWorker worker;
    @Autowired SandboxEvaluator sandbox;
    @MockBean CoursePermissionClient coursePermissions;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("lab-workflow-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
        registry.add("assessment.sandbox.command", LabWorkflowContractTest::pythonCommand);
    }

    private static String pythonCommand() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "py -3"
                : "python3";
    }

    @BeforeEach
    void activeTeacherMembership() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM assessment_lab_score_change_log");
        jdbc.update("DELETE FROM assessment_lab_score");
        jdbc.update("DELETE FROM assessment_lab_evaluation_result");
        jdbc.update("DELETE FROM assessment_lab_testcase");
        jdbc.update("DELETE FROM assessment_lab_submission");
        jdbc.update("DELETE FROM assessment_lab_experiment");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_course_member_projection");
        when(coursePermissions.canManageCourse("course-314", "teacher-314")).thenReturn(true);
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'teacher-314', 'ACTIVE', 1)");
    }

    @Test
    void teacherCanCreateAndPublishALabBeforeStudentsCanSubmit() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String deadline = Instant.parse("2030-01-01T12:00:00Z").toString();

        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", "lab-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"fresh-lab-314","description":"created by issue 314","deadline":"%s",
                                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true,
                                 "testcases":[{"input":"x","expectedOutput":"x","scoreWeight":100,"public":true,"orderNum":1}]}
                                """.formatted(deadline)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("fresh-lab-314"));

        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'fresh-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", "lab-publish-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT status FROM assessment_lab_experiment WHERE id = ?", String.class, labId)).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.lab.published.v2' AND aggregate_id = ?",
                Integer.class, Long.toString(labId))).isEqualTo(1);

        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-reader-314', 'ACTIVE', 1)");
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-reader-314", List.of("STUDENT"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(labId))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void teacherCanCreateLabWithDocumentedStringLanguageAndMetadataFields() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "metadata-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"metadata-lab-314","description":"documented payload","deadline":"2030-01-01T12:00:00Z",
                                 "maxScore":100,"allowedLanguages":"python,java","evaluationMode":"MANUAL","autoEvaluate":false,
                                 "reportRequired":true,"chapterId":42,"attachmentIds":[7,8],"timeLimitMs":45000,"memoryLimitKb":131072}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationMode").value("MANUAL"))
                .andExpect(jsonPath("$.reportRequired").value(true));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
                SELECT chapter_id, attachment_ids, evaluation_mode, report_required, time_limit_ms, memory_limit_kb, allowed_languages
                  FROM assessment_lab_experiment WHERE title = 'metadata-lab-314'
                """)).containsEntry("chapter_id", 42L)
                .containsEntry("attachment_ids", "7,8")
                .containsEntry("evaluation_mode", "MANUAL")
                .containsEntry("report_required", true)
                .containsEntry("time_limit_ms", 45000)
                .containsEntry("memory_limit_kb", 131072)
                .containsEntry("allowed_languages", "python,java");
    }

    @Test
    void studentSubmissionCreatesANewLabVersionAndSharedDurableTask() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-create-submit-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"submit-lab-314","description":"submission boundary","deadline":"2030-01-01T12:00:00Z",
                                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true,
                                 "testcases":[{"input":"x","expectedOutput":"x","scoreWeight":100,"public":true,"orderNum":1}]}
                                """))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'submit-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-publish-submit-314"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file("file", "print('issue-314')".getBytes())
                        .param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "lab-submit-314"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.version").value(1));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission", Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isEqualTo(1);
    }

    @Test
    void manualLabSubmissionIsRecordedWithoutCreatingAnEvaluationTask() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-manual-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-manual-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "manual-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"manual-lab-314\",\"description\":\"teacher review\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":false,\"evaluationMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'manual-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "manual-publish-314"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file("file", "print('manual')".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "manual-submit-314"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationStatus").value("NONE"))
                .andExpect(jsonPath("$.taskId").value(org.hamcrest.Matchers.nullValue()));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT evaluation_status FROM assessment_submission", String.class)).isEqualTo("NONE");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isZero();
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "manual-score-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalScore\":80}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "manual-release-314"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, "student-manual-314")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.finalScore").value(80))
                .andExpect(jsonPath("$.evaluationResult.evaluationStatus").value("NONE"))
                .andExpect(jsonPath("$.evaluationResult.state").value("NONE"))
                .andExpect(jsonPath("$.latestScore.finalScore").value(80));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/labs/{labId}/submissions/{submissionId}/evaluate", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "manual-evaluate-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isEqualTo(1);
    }

    @Test
    void hiddenLabTestcasesAreOmittedFromStudentDetail() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-hidden-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-hidden-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "hidden-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hidden-lab-314\",\"description\":\"private testcase\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"secret\",\"expectedOutput\":\"answer\",\"scoreWeight\":100,\"public\":false,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'hidden-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "hidden-publish-314"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labs/{labId}", labId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testcases").isEmpty());
    }

    @Test
    void completedLabTaskPublishesVersionedSourceGradeAndStudentCanReadThePassiveResult() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-grade-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"grade-lab-314","description":"source grade event","deadline":"2030-01-01T12:00:00Z",
                                "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true,
                                 "testcases":[{"input":"x","expectedOutput":"x","scoreWeight":100,"public":true,"orderNum":1}]}
                                """))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'grade-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-grade-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file("file", "print('score')".getBytes())
                        .param("courseId", "course-314").param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "lab-grade-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        String taskId = jdbc.queryForObject("SELECT id FROM evaluation_task WHERE submission_id = ?", String.class, submissionId);

        worker.runOne("lab-grade-worker", task -> new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("80"), new BigDecimal("100")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.nullValue()));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = 'student-314'", Integer.class, Long.toString(labId))).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "LAB:" + labId + ":student-314")).isZero();

        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-grade-release-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORE_PUBLISHED"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = 'student-314'", Long.class, Long.toString(labId))).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "LAB:" + labId + ":student-314")).isEqualTo(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80));
    }

    @Test
    void studentResultRedactsScoresBeforeReleaseAndRejectsRemovedMember() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-redaction-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-redaction-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "redaction-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"redaction-lab-314\",\"description\":\"score visibility\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'redaction-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "redaction-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print(1)".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "redaction-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "redaction-score-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"manualScore\":80,\"finalScore\":80}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, "student-redaction-314").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission").exists())
                .andExpect(jsonPath("$.submission.autoScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evaluationResult").exists())
                .andExpect(jsonPath("$.evaluationResult.score").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.latestScore").value(org.hamcrest.Matchers.nullValue()));

        jdbc.update("UPDATE assessment_course_member_projection SET membership_status = 'REMOVED' WHERE course_id = 'course-314' AND user_id = 'student-redaction-314'");
        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, "student-redaction-314").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void courseAuthorizationOutageReturnsRetryableServiceUnavailable() throws Exception {
        when(coursePermissions.canManageCourse("course-314", "teacher-314"))
                .thenThrow(new CourseAuthorizationUnavailableException("CRS unavailable"));
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("X-Request-Id", "authorization-outage-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"authorization-outage-314\",\"description\":\"outage\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":false}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COURSE_AUTHORIZATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void automaticLabRequiresPositiveTestcasesWhoseWeightsMatchMaximum() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "auto-validation-empty-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"auto-validation-empty-314\",\"description\":\"validation\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "auto-validation-weight-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"auto-validation-weight-314\",\"description\":\"validation\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":101,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentResultRetainsPublicFeedbackBeforeScoreRelease() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-feedback-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-feedback-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "feedback-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"feedback-lab-314\",\"description\":\"public feedback\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"public-input\",\"expectedOutput\":\"public-output\",\"scoreWeight\":50,\"public\":true,\"orderNum\":1},{\"input\":\"secret-input\",\"expectedOutput\":\"secret-output\",\"scoreWeight\":50,\"public\":false,\"orderNum\":2}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'feedback-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId).header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "feedback-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print(1)".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "feedback-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        List<Long> testcaseIds = jdbc.queryForList("SELECT id FROM assessment_lab_testcase WHERE lab_id = ? ORDER BY order_num", Long.class, labId);
        worker.runOne("feedback-worker-314", task -> new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("100"), new BigDecimal("100"), List.of(
                new AssessmentWorker.LabCaseResult(testcaseIds.get(0), true, new BigDecimal("50"), "public-output", null),
                new AssessmentWorker.LabCaseResult(testcaseIds.get(1), true, new BigDecimal("50"), "secret-output", null))));
        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, "student-feedback-314").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission").exists())
                .andExpect(jsonPath("$.submission.autoScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evaluationResult.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.evaluationResult.caseResults").isArray())
                .andExpect(jsonPath("$.evaluationResult.caseResults.length()").value(1))
                .andExpect(jsonPath("$.evaluationResult.caseResults[0].input").value("public-input"))
                .andExpect(jsonPath("$.evaluationResult.caseResults[0].score").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void workerExecutesPersistedLabCodeAgainstTestcasesAndStoresCaseResults() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-sandbox-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-sandbox-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-sandbox-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"sandbox-lab-314","description":"real persisted code","deadline":"2030-01-01T12:00:00Z",
                                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true,
                                 "testcases":[{"input":"lab\\n","expectedOutput":"LAB\\n","scoreWeight":100,"public":true,"orderNum":1}]}
                                """))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'sandbox-lab-314'", Long.class);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_testcase WHERE lab_id = ?", Integer.class, labId)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-sandbox-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file("file", "import sys\nprint(sys.stdin.read().strip().upper())\n".getBytes())
                        .param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "lab-sandbox-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        worker.runOne("lab-sandbox-worker", sandbox::evaluate);

        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-sandbox-release-314"))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.passedCases").value(1))
                .andExpect(jsonPath("$.caseResults[0].passed").value(true));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_evaluation_result WHERE submission_id = ?", Integer.class, submissionId)).isEqualTo(1);
    }

    @Test
    void sandboxUsesEachLabTimeLimitInsteadOfGlobalTimeout() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-limits-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-limits-314', 'ACTIVE', 1)");
        String payload = "{\"title\":\"%s\",\"description\":\"limit\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"timeLimitMs\":%d,\"memoryLimitKb\":65536,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}";
        for (String[] lab : List.of(new String[]{"fast-limit-lab-314", "10"}, new String[]{"slow-limit-lab-314", "1000"})) {
            mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                            .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", lab[0] + "-create")
                            .contentType(MediaType.APPLICATION_JSON).content(payload.formatted(lab[0], Integer.parseInt(lab[1]))))
                    .andExpect(status().isCreated());
            Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = ?", Long.class, lab[0]);
            mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                            .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", lab[0] + "-publish"))
                    .andExpect(status().isOk());
            mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                            .file("file", "import os,time\nassert os.getenv('OJ_MEMORY_LIMIT_KB') == '65536'\ntime.sleep(0.1)\nprint('x')".getBytes()).param("language", "python")
                            .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", lab[0] + "-submit"))
                    .andExpect(status().isCreated());
            String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
            worker.runOne(lab[0] + "-worker", sandbox::evaluate);
            if ("10".equals(lab[1])) {
                jdbc.update("UPDATE evaluation_task SET next_attempt_at = TIMESTAMP '1970-01-01 00:00:00' WHERE submission_id = ?", submissionId);
                worker.runOne(lab[0] + "-worker-retry", sandbox::evaluate);
                jdbc.update("UPDATE evaluation_task SET next_attempt_at = TIMESTAMP '1970-01-01 00:00:00' WHERE submission_id = ?", submissionId);
                worker.runOne(lab[0] + "-worker-final", sandbox::evaluate);
            }
            String status = jdbc.queryForObject("SELECT evaluation_status FROM assessment_submission WHERE id = ?", String.class, submissionId);
            if ("10".equals(lab[1])) org.assertj.core.api.Assertions.assertThat(status).isEqualTo("SANDBOX_TIMEOUT");
            else org.assertj.core.api.Assertions.assertThat(status).isEqualTo("ACCEPTED");
        }
    }

    @Test
    void globalTeacherWhoCannotManageThisCourseCannotCreateLabs() throws Exception {
        String token = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-as-student-314", List.of("TEACHER"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'teacher-as-student-314', 'ACTIVE', 1)");
        when(coursePermissions.canManageCourse("course-314", "teacher-as-student-314")).thenReturn(false);

        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + token).header("X-Request-Id", "teacher-not-manager-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"forbidden\",\"description\":\"must not create\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true}"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_experiment WHERE title = 'forbidden'", Integer.class)).isZero();
    }

    @Test
    void teacherCanManageLabLifecycleAndReadSubmissionHistory() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-history-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-history-314', 'ACTIVE', 1)");
        String createPayload = """
                {"title":"lifecycle-lab-314","description":"lifecycle coverage","deadline":"2030-01-01T12:00:00Z",
                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true,
                 "testcases":[{"input":"x\\n","expectedOutput":"X\\n","scoreWeight":100,"public":true,"orderNum":1}]}
                """;
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lifecycle-create-314")
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'lifecycle-lab-314'", Long.class);

        mockMvc.perform(put("/api/v1/labs/{labId}", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lifecycle-update-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload.replace("lifecycle-lab-314", "lifecycle-lab-updated-314")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("lifecycle-lab-updated-314"));
        mockMvc.perform(get("/api/v1/labs/{labId}", labId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lifecycle-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/labs/{labId}", labId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("lifecycle-lab-updated-314"))
                .andExpect(jsonPath("$.testcases[0].expectedOutput").value("X\n"));
        mockMvc.perform(post("/api/v1/labs/{labId}/close", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lifecycle-close-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lifecycle-release-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORE_PUBLISHED"));
    }

    @Test
    void teacherCanDeleteOnlyDraftLab() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "delete-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"delete-lab-314\",\"description\":\"draft\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'delete-lab-314'", Long.class);
        mockMvc.perform(delete("/api/v1/labs/{labId}", labId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT deleted FROM assessment_lab_experiment WHERE id = ?", Boolean.class, labId)).isTrue();
    }

    @Test
    void submissionHistoryIsScopedToStudentAndTeacherCanReadAllVersions() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-query-314", List.of("STUDENT"));
        String otherStudentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-other-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-query-314', 'ACTIVE', 1)");
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-other-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "query-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"query-lab-314\",\"description\":\"query\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'query-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "query-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print(1)".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "query-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        worker.runOne("query-worker-314", task -> new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("67"), new BigDecimal("100")));

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$[0].autoScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].finalScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].isLatest").value(true));
        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(submissionId))
                .andExpect(jsonPath("$.autoScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.finalScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.hasFile").value(true));
        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$[0].autoScore").value(67));
    }

    @Test
    void studentCanSubmitOnlineCodeWithoutUploadingAFile() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-code-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-code-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "code-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"code-lab-314\",\"description\":\"code\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'code-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "code-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .param("language", "python").param("code", "print('code')")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "code-submit-314"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationStatus").value("PENDING"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission WHERE lab_id = ?", Integer.class, labId)).isEqualTo(1);
    }

    @Test
    void teacherScorePersistsFinalScoreAndPublishesSourceGrade() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-score-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-score-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "score-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"score-lab-314\",\"description\":\"score\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'score-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "score-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print(1)".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "score-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "score-write-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"manualScore\":80,\"finalScore\":80,\"comment\":\"checked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(80))
                .andExpect(jsonPath("$.hasChangeLogs").value(false));
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "score-change-without-reason-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalScore\":90}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "score-change-with-reason-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalScore\":90,\"changeReason\":\"rubric correction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(90))
                .andExpect(jsonPath("$.hasChangeLogs").value(true));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_score_change_log WHERE submission_id = ?", Integer.class, submissionId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT old_final_score FROM assessment_lab_score_change_log WHERE submission_id = ?", BigDecimal.class, submissionId)).isEqualByComparingTo("80");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT new_final_score FROM assessment_lab_score_change_log WHERE submission_id = ?", BigDecimal.class, submissionId)).isEqualByComparingTo("90");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT reason FROM assessment_lab_score_change_log WHERE submission_id = ?", String.class, submissionId)).isEqualTo("rubric correction");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT final_score FROM assessment_lab_submission WHERE submission_id = ?", BigDecimal.class, submissionId)).isEqualByComparingTo("90");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT submit_status FROM assessment_lab_submission WHERE submission_id = ?", String.class, submissionId)).isEqualTo("SCORED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = ?", Integer.class, Long.toString(labId), "student-score-314")).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "LAB:" + labId + ":student-score-314")).isZero();
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "score-release-314"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT score FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = ?", BigDecimal.class, Long.toString(labId), "student-score-314")).isEqualByComparingTo("90");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "LAB:" + labId + ":student-score-314")).isEqualTo(1);
        mockMvc.perform(get("/api/v1/labs/{labId}/statistics", labId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labId").value(labId))
                .andExpect(jsonPath("$.submittedCount").value(1))
                .andExpect(jsonPath("$.totalStudentCount").value(1));
        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, "student-score-314")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labId").value(labId))
                .andExpect(jsonPath("$.studentId").value("student-score-314"))
                .andExpect(jsonPath("$.latestScore.finalScore").value(90));
    }

    @Test
    void releasePrefersTeacherFinalScoreOverNewerAutomaticSubmission() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-final-priority-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-final-priority-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "final-priority-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"final-priority-lab-314\",\"description\":\"final score wins\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'final-priority-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "final-priority-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print('first')".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "final-priority-first-314"))
                .andExpect(status().isCreated());
        String firstSubmissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ? AND submission_version = 1", String.class, labId);
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, firstSubmissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "final-priority-score-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalScore\":80}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print('second')".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "final-priority-second-314"))
                .andExpect(status().isCreated());
        worker.runOne("final-priority-worker-first-314", task -> new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("30"), new BigDecimal("100")));
        worker.runOne("final-priority-worker-second-314", task -> new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("20"), new BigDecimal("100")));

        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "final-priority-release-314"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT score FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = ?", BigDecimal.class, Long.toString(labId), "student-final-priority-314"))
                .isEqualByComparingTo("80");
    }

    @Test
    void releasedTeacherFinalScoreSurvivesEvaluationReplay() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-replay-final-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-replay-final-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-final-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"replay-final-lab-314\",\"description\":\"final score survives replay\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":[\"python\"],\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'replay-final-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-final-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print(1)".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "replay-final-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        for (int attempt = 0; attempt < 3; attempt++) {
            worker.runOne("replay-final-worker-314", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_ERROR"));
            jdbc.update("UPDATE evaluation_task SET next_attempt_at = TIMESTAMP '1970-01-01 00:00:00' WHERE state = 'RETRY_WAIT'");
        }
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-final-score-314")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalScore\":80}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-final-release-314"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/evaluate", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-final-requeue-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
        worker.runOne("replay-final-success-worker-314", task -> new AssessmentWorker.EvaluationOutcome(true, "ACCEPTED", new BigDecimal("20"), new BigDecimal("100")));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT score FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = ?", BigDecimal.class, Long.toString(labId), "student-replay-final-314"))
                .isEqualByComparingTo("80");
    }

    @Test
    void teacherCanReplayFailedLabSubmissionThroughLabEndpoint() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-replay-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-replay-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"replay-lab-314\",\"description\":\"replay\",\"deadline\":\"2030-01-01T12:00:00Z\",\"maxScore\":100,\"allowedLanguages\":\"python\",\"autoEvaluate\":true,\"testcases\":[{\"input\":\"x\",\"expectedOutput\":\"x\",\"scoreWeight\":100,\"public\":true,\"orderNum\":1}]}"))
                .andExpect(status().isCreated());
        Long labId = jdbc.queryForObject("SELECT id FROM assessment_lab_experiment WHERE title = 'replay-lab-314'", Long.class);
        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-publish-314"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId).file("file", "print(1)".getBytes()).param("language", "python")
                        .header("Authorization", "Bearer " + studentToken).header("X-Request-Id", "replay-submit-314"))
                .andExpect(status().isCreated());
        String submissionId = jdbc.queryForObject("SELECT submission_id FROM assessment_lab_submission WHERE lab_id = ?", String.class, labId);
        for (int attempt = 0; attempt < 3; attempt++) {
            worker.runOne("replay-worker-314", task -> AssessmentWorker.EvaluationOutcome.failed("SANDBOX_ERROR"));
            jdbc.update("UPDATE evaluation_task SET next_attempt_at = TIMESTAMP '1970-01-01 00:00:00' WHERE state = 'RETRY_WAIT'");
        }

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/labs/{labId}/submissions/{submissionId}/evaluate", labId, submissionId)
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "replay-write-314"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
    }
}
