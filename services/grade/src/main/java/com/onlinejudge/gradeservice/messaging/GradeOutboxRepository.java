package com.onlinejudge.gradeservice.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class GradeOutboxRepository {
    private final JdbcTemplate jdbc;

    public GradeOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OutboxEvent> pending(int limit) {
        return jdbc.query("""
                SELECT event_id,event_type,correlation_id,payload_json
                  FROM grade_event_outbox
                 WHERE delivery_status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP
                 ORDER BY created_at,event_id LIMIT ?
                """, (rs, ignored) -> new OutboxEvent(rs.getString("event_id"), rs.getString("event_type"),
                rs.getString("correlation_id"), rs.getString("payload_json")), limit);
    }

    public void markDelivered(String eventId) {
        jdbc.update("""
                UPDATE grade_event_outbox
                   SET delivery_status='DELIVERED', delivery_attempt=delivery_attempt+1,
                       delivered_at=CURRENT_TIMESTAMP, last_error=NULL
                 WHERE event_id=? AND delivery_status='PENDING'
                """, eventId);
    }

    public void recordFailure(String eventId, String error) {
        Integer attempts = jdbc.queryForObject(
                "SELECT delivery_attempt FROM grade_event_outbox WHERE event_id=?", Integer.class, eventId);
        long delaySeconds = Math.min(60, 1L << Math.min(6, attempts == null ? 0 : attempts));
        jdbc.update("""
                UPDATE grade_event_outbox
                   SET delivery_attempt=delivery_attempt+1, next_attempt_at=?, last_error=?
                 WHERE event_id=? AND delivery_status='PENDING'
                """, Timestamp.from(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS)), abbreviate(error), eventId);
    }

    private static String abbreviate(String error) {
        if (error == null) return "unknown broker failure";
        return error.length() <= 1024 ? error : error.substring(0, 1024);
    }

    public record OutboxEvent(String eventId, String eventType, String correlationId, String payloadJson) { }
}
