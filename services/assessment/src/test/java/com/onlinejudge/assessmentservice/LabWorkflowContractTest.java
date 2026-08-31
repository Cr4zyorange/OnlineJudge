package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;
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

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("lab-workflow-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void activeTeacherMembership() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM assessment_lab_submission");
        jdbc.update("DELETE FROM assessment_lab_experiment");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_course_member_projection");
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
                                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true}
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

        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-reader-314', 'ACTIVE', 1)");
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-reader-314", List.of("STUDENT"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(labId))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
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
                                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true}
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
    void completedLabTaskPublishesVersionedSourceGradeAndStudentCanReadThePassiveResult() throws Exception {
        String teacherToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "teacher-314", List.of("TEACHER"));
        String studentToken = TestJwtFactory.userToken(KEY, "lab-workflow-kid", "student-314", List.of("STUDENT"));
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-314', 'student-314', 'ACTIVE', 1)");
        mockMvc.perform(post("/api/v1/courses/{courseId}/labs", "course-314")
                        .header("Authorization", "Bearer " + teacherToken).header("X-Request-Id", "lab-grade-create-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"grade-lab-314","description":"source grade event","deadline":"2030-01-01T12:00:00Z",
                                 "maxScore":100,"allowedLanguages":["python"],"autoEvaluate":true}
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
                .andExpect(jsonPath("$.score").value(80));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type = 'LAB' AND source_id = ? AND student_id = 'student-314'", Long.class, Long.toString(labId))).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2' AND aggregate_id = ?", Integer.class, "LAB:" + labId + ":student-314")).isEqualTo(1);
    }
}
