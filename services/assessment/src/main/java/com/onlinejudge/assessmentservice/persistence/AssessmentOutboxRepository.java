package com.onlinejudge.assessmentservice.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Local producer facts; RabbitMQ delivery belongs to the relay and never the submission transaction. */
@Repository
public class AssessmentOutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AssessmentOutboxRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void append(String eventType, String aggregateType, String aggregateId, long aggregateVersion,
                       String correlationId, Map<String, Object> payload, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO assessment_event_outbox (event_id, event_type, payload_version, aggregate_type,
                     aggregate_id, aggregate_version, occurred_at, correlation_id, payload_json, state, created_at)
                    VALUES (?, ?, 2, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                    """, UUID.randomUUID().toString(), eventType, aggregateType, aggregateId, aggregateVersion,
                    Timestamp.from(now), correlationId, mapper.writeValueAsString(payload), Timestamp.from(now));
        } catch (Exception exception) {
            throw new IllegalStateException("assessment outbox serialization failed", exception);
        }
    }

    public int countByType(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = ?", Integer.class, type);
    }
}
