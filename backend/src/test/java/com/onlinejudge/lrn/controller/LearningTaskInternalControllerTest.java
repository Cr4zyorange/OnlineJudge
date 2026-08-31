package com.onlinejudge.lrn.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bounded Course -> LRN recent-task summary contract (learning.openapi.json):
 * an authenticated Learning service principal with learning.tasks.read scope gets
 * at most five deadline-ordered tasks for one member in one course; missing or
 * invalid service identity is 401, an authenticated principal without the scope is 403.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_internal_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@ContextConfiguration(initializers = LearningTaskInternalControllerTest.TrustBundleInitializer.class)
@AutoConfigureMockMvc
class LearningTaskInternalControllerTest {
    private static final ObjectMapper TOKENS = new ObjectMapper();
    private static final KeyPair IDENTITY_KEY = keyPair();
    private static final String KID = "learning-test-kid";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedMemberTasks() {
        jdbcTemplate.update("DELETE FROM lrn_learning_task");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_course");
        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (501, 'Java Programming', 'course description', 901, 'PUBLISHED')
                """);
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES (501, 601, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP)
                """);
        insertTask(601L, 501L, "HWK", 701L, "HOMEWORK", "Homework Due Later",
                LocalDateTime.now().plusDays(2), 10, "IN_PROGRESS", "/courses/501/homeworks/701");
        insertTask(601L, 501L, "LAB", 801L, "EXPERIMENT", "Lab Due Sooner",
                LocalDateTime.now().plusDays(1), 0, "NOT_STARTED", "/courses/501/labs/801");
    }

    @Test
    void servicePrincipalWithTasksScopeGetsBoundedRecentTasksForOneMember() throws Exception {
        mockMvc.perform(get("/internal/v2/learning/tasks/recent")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + serviceToken(List.of("learning.tasks.read")))
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .param("courseId", "501")
                        .param("userId", "601")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.items[0].title").value("Lab Due Sooner"))
                .andExpect(jsonPath("$.items[0].taskType").value("EXPERIMENT"))
                .andExpect(jsonPath("$.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.items[1].title").value("Homework Due Later"))
                .andExpect(jsonPath("$.items[1].courseId").value(501));
    }

    @Test
    void recentTaskLimitIsBoundedToFiveRegardlessOfRequestedLimit() throws Exception {
        for (int index = 0; index < 7; index++) {
            insertTask(601L, 501L, "HWK", 900L + index, "HOMEWORK", "Bulk Homework " + index,
                    LocalDateTime.now().plusDays(10 + index), 0, "NOT_STARTED", "/courses/501/homeworks/" + (900L + index));
        }
        mockMvc.perform(get("/internal/v2/learning/tasks/recent")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + serviceToken(List.of("learning.tasks.read")))
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .param("courseId", "501")
                        .param("userId", "601")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(9));
    }

    @Test
    void missingOrInvalidServiceIdentityIsRejectedWithServiceIdentityInvalid() throws Exception {
        mockMvc.perform(get("/internal/v2/learning/tasks/recent")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .param("courseId", "501")
                        .param("userId", "601"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_INVALID"));

        mockMvc.perform(get("/internal/v2/learning/tasks/recent")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer not-a-jwt")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .param("courseId", "501")
                        .param("userId", "601"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_INVALID"));
    }

    @Test
    void servicePrincipalWithoutTasksScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/internal/v2/learning/tasks/recent")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + serviceToken(List.of("notifications.reconcile")))
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .param("courseId", "501")
                        .param("userId", "601"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_FORBIDDEN"));
    }

    @Test
    void serviceTokenForAnotherAudienceIsRejected() throws Exception {
        String wrongAudience = header("RS256", KID) + "." + payload("course-service", "assessment", List.of("learning.tasks.read"))
                + "." + sign("course-service", "assessment", List.of("learning.tasks.read"));
        mockMvc.perform(get("/internal/v2/learning/tasks/recent")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + wrongAudience)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .param("courseId", "501")
                        .param("userId", "601"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_INVALID"));
    }

    private void insertTask(long userId, long courseId, String sourceModule, long sourceId, String taskType,
                            String title, LocalDateTime deadline, int progress, String status, String actionUrl) {
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_task
                    (user_id, course_id, source_module, source_id, task_type, title, deadline, progress, status, action_url, snapshot_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, courseId, sourceModule, sourceId, taskType, title, deadline, progress, status, actionUrl);
    }

    private static String serviceToken(List<String> scopes) {
        return header("RS256", KID) + "." + payload("course-service", "learning", scopes)
                + "." + sign("course-service", "learning", scopes);
    }

    private static String header(String algorithm, String kid) {
        return encode(Map.of("alg", algorithm, "typ", "JWT", "kid", kid));
    }

    private static String payload(String subject, String audience, List<String> scopes) {
        return encode(Map.of(
                "sub", subject,
                "aud", audience,
                "scopes", scopes,
                "iat", Instant.now().getEpochSecond(),
                "exp", Instant.now().plusSeconds(300).getEpochSecond(),
                "iss", "onlinejudge.identity.v2"
        ));
    }

    private static String sign(String subject, String audience, List<String> scopes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(IDENTITY_KEY.getPrivate());
            signature.update((header("RS256", KID) + "." + payload(subject, audience, scopes)).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String jwks() {
        RSAPublicKey key = (RSAPublicKey) IDENTITY_KEY.getPublic();
        try {
            return TOKENS.writeValueAsString(Map.of("keys", List.of(Map.of(
                    "kty", "RSA", "use", "sig", "alg", "RS256", "kid", KID,
                    "n", unsigned(key.getModulus()), "e", unsigned(key.getPublicExponent())
            ))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(TOKENS.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    static final class TrustBundleInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of("onlinejudge.identity.jwks.trust-bundle=" + jwks())
                    .applyTo(context.getEnvironment());
        }
    }
}
