package com.onlinejudge.courseservice.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class CourseOutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CourseOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(String eventType, String aggregateType, String aggregateId, long aggregateVersion,
                       String correlationId, Map<String, Object> payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO course_event_outbox
                    (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
                     correlation_id, payload_json, routing_key, delivery_status, attempt_count, next_attempt_at)
                    VALUES (?, ?, 2, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), eventType, aggregateType, aggregateId, aggregateVersion,
                    correlationId, objectMapper.writeValueAsString(payload), eventType);
        } catch (Exception exception) {
            throw new IllegalStateException("course outbox write failed", exception);
        }
    }

    public List<OutboxRecord> due(int limit) {
        return jdbcTemplate.query("""
                SELECT id, event_id, event_type, aggregate_type, aggregate_id, aggregate_version, correlation_id, payload_json, routing_key
                  FROM course_event_outbox
                 WHERE delivery_status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                 ORDER BY id LIMIT ?
                """, (rs, row) -> new OutboxRecord(rs.getLong("id"), rs.getString("event_id"), rs.getString("event_type"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getLong("aggregate_version"),
                rs.getString("correlation_id"), rs.getString("payload_json"), rs.getString("routing_key")), limit);
    }

    public void published(long id) {
        jdbcTemplate.update("UPDATE course_event_outbox SET delivery_status='PUBLISHED', published_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public void retry(long id, String error) {
        jdbcTemplate.update("UPDATE course_event_outbox SET attempt_count=attempt_count+1, last_error=?, next_attempt_at=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", error, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(5)), id);
    }

    public record OutboxRecord(long id, String eventId, String eventType, String aggregateType, String aggregateId,
                              long aggregateVersion, String correlationId, String payloadJson, String routingKey) { }
}
