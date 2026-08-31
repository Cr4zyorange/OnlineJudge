package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.persistence.IdentitySecurityVersionDeadLetterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest
class IdentitySecurityVersionDeadLetterRepositoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentitySecurityVersionDeadLetterRepository deadLetters;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_identity_security_version_dead_letter");
    }

    @Test
    void securityVersionDeadLettersHaveASeparateDurableAuditBoundary() {
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE UPPER(TABLE_NAME) = 'ASSESSMENT_IDENTITY_SECURITY_VERSION_DEAD_LETTER'
                """, Integer.class);

        assertThat(tables).isEqualTo(1);

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        deadLetters.capture(eventId, correlationId, "{\"payloadVersion\":1}", "payloadVersion must be 2", Instant.now());
        deadLetters.capture(eventId, correlationId, "{\"payloadVersion\":1}", "payloadVersion must be 2", Instant.now());

        var captured = deadLetters.find(eventId).orElseThrow();
        assertThat(captured.correlationId()).isEqualTo(correlationId);
        assertThat(captured.payloadJson()).isEqualTo("{\"payloadVersion\":1}");
        assertThat(captured.failureReason()).contains("payloadVersion");
        assertThat(captured.deliveryAttempt()).isEqualTo(2);
        assertThat(captured.replayCount()).isZero();
        assertThat(deadLetters.markReplayed(eventId, Instant.now())).isTrue();
        assertThat(deadLetters.find(eventId).orElseThrow().replayCount()).isEqualTo(1);
    }
}
