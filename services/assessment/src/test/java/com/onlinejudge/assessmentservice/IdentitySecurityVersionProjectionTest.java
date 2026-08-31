package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import com.onlinejudge.assessmentservice.service.IdentitySecurityVersionProjectionService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Identity security events revoke old bearer claims locally even while Identity itself is unavailable. */
@SpringBootTest
@AutoConfigureMockMvc
class IdentitySecurityVersionProjectionTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();
    @Autowired IdentitySecurityVersionProjectionService securityVersions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("security-version-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_identity_security_version_event_inbox");
        jdbc.update("DELETE FROM assessment_deferred_identity_security_version_event");
        jdbc.update("DELETE FROM assessment_identity_security_version_gap");
        jdbc.update("DELETE FROM assessment_identity_security_version");
        jdbc.update("DELETE FROM assessment_course_member_projection");
        jdbc.update("DELETE FROM assessment_homework_review_log");
        jdbc.update("DELETE FROM assessment_homework_evaluation");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_homework_submission");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES ('course-security', 'student-security', 'ACTIVE', 1)");
    }

    @Test
    void gapIsDurableButTheHighestObservedMinimumImmediatelyRejectsOldBearerUntilReplayClosesIt() throws Exception {
        var late = securityVersions.apply(new IdentitySecurityVersionProjectionService.SecurityVersionChanged("event-3", "student-security", 3, "ROLE_CHANGED"));
        assertThat(late.decision()).isEqualTo("GAP");
        assertThat(securityVersions.minimumFor("student-security")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_identity_security_version_gap", Integer.class)).isEqualTo(1);

        String v1 = TestJwtFactory.userToken(KEY, "security-version-kid", "student-security", List.of("STUDENT"), 1);
        mockMvc.perform(post("/api/v1/submissions").header("Authorization", "Bearer " + v1).header("X-Request-Id", "security-old-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sourceType\":\"LAB\",\"sourceId\":\"security-lab\",\"courseId\":\"course-security\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(securityVersions.apply(new IdentitySecurityVersionProjectionService.SecurityVersionChanged("event-2", "student-security", 2, "LOGOUT")).decision()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT aggregate_version FROM assessment_identity_security_version WHERE user_id='student-security'", Long.class)).isEqualTo(3L);
        assertThat(securityVersions.apply(new IdentitySecurityVersionProjectionService.SecurityVersionChanged("event-2", "student-security", 2, "LOGOUT")).decision()).isEqualTo("DUPLICATE");

        String v3 = TestJwtFactory.userToken(KEY, "security-version-kid", "student-security", List.of("STUDENT"), 3);
        mockMvc.perform(post("/api/v1/submissions").header("Authorization", "Bearer " + v3).header("X-Request-Id", "security-current-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sourceType\":\"LAB\",\"sourceId\":\"security-lab\",\"courseId\":\"course-security\"}"))
                .andExpect(status().isCreated());
    }
}
