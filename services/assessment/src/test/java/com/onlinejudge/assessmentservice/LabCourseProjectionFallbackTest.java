package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Issue #357: a partial Course projection must fail closed without writing LAB facts. */
@SpringBootTest(properties = "assessment.worker.enabled=false")
@AutoConfigureMockMvc
class LabCourseProjectionFallbackTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @MockBean CoursePermissionClient coursePermissions;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("lab-fallback-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void resetFacts() {
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM assessment_lab_score");
        jdbc.update("DELETE FROM assessment_lab_submission");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_course_projection_gap");
        jdbc.update("DELETE FROM assessment_course_member_projection");
        jdbc.update("DELETE FROM assessment_lab_experiment");
        jdbc.update("""
                INSERT INTO assessment_lab_experiment
                    (id, course_id, title, description, status, deadline, max_score, allowed_languages,
                     auto_evaluate, evaluation_mode, report_required, time_limit_ms, memory_limit_kb,
                     attachment_ids, deleted, created_by, created_at, updated_at)
                VALUES (35701, 'course-357', 'projection fallback', 'fallback test', 'PUBLISHED',
                        TIMESTAMP '2030-01-01 00:00:00', 100, 'python', TRUE, 'DOCKER_IO', FALSE,
                        30000, 262144, '', FALSE, 'teacher-357', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO assessment_course_member_projection
                    (course_id, user_id, membership_status, member_version)
                VALUES ('course-357', 'student-357', 'ACTIVE', 1)
                """);
        jdbc.update("""
                INSERT INTO assessment_course_projection_gap
                    (course_id, user_id, expected_version, observed_version)
                VALUES ('course-357', 'student-357', 1, 2)
                """);
    }

    @Test
    void courseAuthorizationIsUsedWhenProjectionHasAGap() throws Exception {
        when(coursePermissions.canViewCourse("course-357", "student-357", "lab-357-submit"))
                .thenReturn(true);
        mockMvc.perform(multipart("/api/v1/labs/35701/submissions")
                        .file("file", "print('ok')".getBytes())
                        .param("language", "python")
                        .header("Authorization", "Bearer " + token("student-357", "STUDENT"))
                        .header("X-Request-Id", "lab-357-submit"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isEqualTo(1);
        verify(coursePermissions).canViewCourse("course-357", "student-357", "lab-357-submit");
    }

    @Test
    void unavailableCourseLeavesSubmissionAndTaskFactsUntouched() throws Exception {
        when(coursePermissions.canViewCourse("course-357", "student-357", "lab-357-outage"))
                .thenThrow(new CourseAuthorizationUnavailableException("CRS unavailable"));
        mockMvc.perform(multipart("/api/v1/labs/35701/submissions")
                        .file("file", "print('blocked')".getBytes())
                        .param("language", "python")
                        .header("Authorization", "Bearer " + token("student-357", "STUDENT"))
                        .header("X-Request-Id", "lab-357-outage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COURSE_AUTHORIZATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("lab-357-outage"))
                .andExpect(jsonPath("$.retryable").value(true));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_score", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade", Integer.class)).isZero();
        verify(coursePermissions).canViewCourse("course-357", "student-357", "lab-357-outage");
    }

    private String token(String userId, String role) {
        return TestJwtFactory.userToken(KEY, "lab-fallback-kid", userId, List.of(role));
    }
}
