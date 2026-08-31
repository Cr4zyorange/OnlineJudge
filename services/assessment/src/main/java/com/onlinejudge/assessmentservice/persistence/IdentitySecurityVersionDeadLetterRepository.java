package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** Durable, Assessment-owned audit trail for terminal Identity security-version envelopes. */
@Repository
public class IdentitySecurityVersionDeadLetterRepository {
    private final JdbcTemplate jdbc;

    public IdentitySecurityVersionDeadLetterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void capture(String eventId, String correlationId, String payloadJson, String failureReason, Instant receivedAt) {
        jdbc.update("""
                INSERT INTO assessment_identity_security_version_dead_letter
                    (event_id, correlation_id, payload_json, failure_reason, delivery_attempt, replay_count, received_at)
                VALUES (?, ?, ?, ?, 1, 0, ?)
                ON DUPLICATE KEY UPDATE correlation_id=VALUES(correlation_id), payload_json=VALUES(payload_json),
                    failure_reason=VALUES(failure_reason), delivery_attempt=delivery_attempt+1, received_at=VALUES(received_at)
                """, eventId, correlationId, payloadJson, bounded(failureReason), Timestamp.from(receivedAt));
    }

    public Optional<DeadLetter> find(String eventId) {
        return jdbc.query("""
                SELECT event_id, correlation_id, payload_json, failure_reason, delivery_attempt, replay_count
                FROM assessment_identity_security_version_dead_letter WHERE event_id=?
                """, (rs, ignored) -> new DeadLetter(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6)), eventId)
                .stream().findFirst();
    }

    public boolean markReplayed(String eventId, Instant now) {
        return jdbc.update("""
                UPDATE assessment_identity_security_version_dead_letter
                SET replay_count=replay_count+1, replayed_at=? WHERE event_id=?
                """, Timestamp.from(now), eventId) == 1;
    }

    private String bounded(String reason) {
        String value = reason == null || reason.isBlank() ? "INVALID_IDENTITY_SECURITY_VERSION_ENVELOPE" : reason;
        return value.substring(0, Math.min(value.length(), 256));
    }

    public record DeadLetter(String eventId, String correlationId, String payloadJson, String failureReason,
                             int deliveryAttempt, int replayCount) { }
}
