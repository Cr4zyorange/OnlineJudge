package com.onlinejudge.assessmentservice;

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

import java.security.KeyPair;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/** v2 identity and Course projection are both mandatory; gateway headers never grant submission authority. */
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentSecurityAndProjectionTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("assessment-test-kid", KEY));
        registry.add("assessment.identity.jwks-uri", () -> "http://127.0.0.1:1/identity/jwks.json");
    }

    @BeforeEach
    void projection() {
        // HWK facts reference evaluation_task, so drop them first when the shared JVM left rows behind.
        jdbc.update("DELETE FROM assessment_homework_evaluation");
        jdbc.update("DELETE FROM assessment_homework_review_log");
        jdbc.update("DELETE FROM assessment_homework_submission");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_course_member_projection");
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-7', 'student-42', 'ACTIVE', 1)");
    }

    @Test
    void forgedHeadersAreRejectedAndAnAuthenticatedStudentCannotBypassTheCanonicalLabSubmissionRoute() throws Exception {
        mockMvc.perform(post("/api/v1/submissions").header("X-User-Id", "student-42").header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sourceType\":\"LAB\",\"sourceId\":\"lab-1\",\"courseId\":\"course-7\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/submissions").header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "assessment-test-kid", "student-42", List.of("STUDENT")))
                        .header("X-Request-Id", "8f647722-1c27-4dcb-b388-6004a0d8929d").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"LAB\",\"sourceId\":\"lab-1\",\"courseId\":\"course-7\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void genericSubmissionEndpointCannotForgeALabThatHasNoPublishedLabFact() throws Exception {
        String token = TestJwtFactory.userToken(KEY, "assessment-test-kid", "student-42", List.of("STUDENT"));
        mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token).header("X-Request-Id", "forged-lab-314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"LAB\",\"sourceId\":\"not-a-lab\",\"courseId\":\"course-7\",\"contentRef\":\"attacker-controlled\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission WHERE source_type = 'LAB'", Integer.class)).isZero();
    }
}
