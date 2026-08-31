package com.onlinejudge.courseservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.security.TestJwtFactory;
import com.onlinejudge.courseservice.service.IdentitySecurityVersionEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A locally verified JWT becomes invalid immediately after Course durably
 * projects Identity's newer securityVersion; it must not remain valid until
 * exp merely because JWKS is still available.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@AutoConfigureMockMvc
class CourseSecurityVersionProjectionTest {
    private static final KeyPair KEY_PAIR = TestJwtFactory.rsaKeyPair();
    private static final String JWKS = TestJwtFactory.jwks("course-security-kid", KEY_PAIR);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IdentitySecurityVersionEventConsumer consumer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("course.identity.jwks-trust-bundle", () -> JWKS);
        registry.add("course.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM event_inbox");
        jdbcTemplate.update("DELETE FROM course_event_outbox");
        deleteIfPresent("course_membership_reconciliation_checkpoint");
        deleteIfPresent("course_roster_reconciliation_checkpoint");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        deleteIfPresent("crs_announcement");
        deleteIfPresent("crs_resource");
        CourseTestDataCleanup.deleteChapters(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM crs_course");
    }

    private void deleteIfPresent(String table) {
        try {
            jdbcTemplate.update("DELETE FROM " + table);
        } catch (BadSqlGrammarException absentInThisSchemaGeneration) {
            // The v2 security consumer is deliberately tested against both the
            // pre-DB-CRS H2 shape and the canonical Course schema evolution.
        }
    }

    @Test
    void newerCanonicalSecurityVersionRejectsOldJwtAndGapReplayConvergesDurably() throws Exception {
        String v1 = TestJwtFactory.userToken(KEY_PAIR, "course-security-kid", "4101", List.of("TEACHER"), List.of("course:manage"), 1);
        create(v1, "before revocation").andExpect(status().isCreated());

        consumer.consume(envelope("security-4101-v2", 2));
        consumer.consume(envelope("security-4101-v2", 2));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_inbox WHERE event_id = 'security-4101-v2'", Integer.class)).isEqualTo(1);

        create(v1, "rejected after revocation").andExpect(status().isUnauthorized());
        String v2 = TestJwtFactory.userToken(KEY_PAIR, "course-security-kid", "4101", List.of("TEACHER"), List.of("course:manage"), 2);
        create(v2, "accepted at projected version").andExpect(status().isCreated());

        consumer.consume(envelope("security-4101-v4", 4));
        assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM event_inbox WHERE event_id = 'security-4101-v4'", String.class)).isEqualTo("DEFERRED_GAP");
        create(v2, "gap still fails closed at newer version").andExpect(status().isUnauthorized());

        consumer.consume(envelope("security-4101-v3", 3));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_inbox WHERE aggregate_id = '4101' AND processing_status = 'APPLIED'", Integer.class)).isEqualTo(3);
        String v4 = TestJwtFactory.userToken(KEY_PAIR, "course-security-kid", "4101", List.of("TEACHER"), List.of("course:manage"), 4);
        create(v4, "accepted after durable replay").andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/courses")
                .header("Authorization", token)
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private String envelope(String eventId, long securityVersion) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", "identity.security-version.changed.v2");
        event.put("payloadVersion", 2);
        event.put("aggregateType", "identity-user");
        event.put("aggregateId", "4101");
        event.put("aggregateVersion", securityVersion);
        event.put("occurredAt", Instant.parse("2026-08-31T00:00:00Z").toString());
        event.put("correlationId", "4c6a2972-a4da-4556-98a3-bf2f8b5a932c");
        event.put("payload", Map.of("userId", "4101", "securityVersion", securityVersion, "changeReason", "LOGOUT"));
        return objectMapper.writeValueAsString(event);
    }
}
