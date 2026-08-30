package com.onlinejudge.hwk.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.OutboxRecord;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.hwk.domain.Homework;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Assessment owns this outbox.  The caller must invoke it from the same local
 * transaction that commits the Assessment business fact; broker availability is
 * deliberately not part of that transaction.
 */
@Repository
public class AssessmentEventOutboxRepository {
    public static final String HOMEWORK_PUBLISHED = "assessment.homework.published.v2";
    public static final String RECEIVER_SCOPE = "COURSE_ACTIVE_STUDENTS";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AssessmentEventOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void appendHomeworkPublished(Homework homework) {
        LocalDateTime occurredAt = homework.publishedAt();
        if (occurredAt == null) {
            throw new IllegalArgumentException("published homework must have publishedAt");
        }
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", String.valueOf(homework.courseId()));
        payload.put("homeworkId", String.valueOf(homework.id()));
        payload.put("title", homework.title());
        payload.put("deadline", asRfc3339(homework.deadline()));
        payload.put("receiverScope", RECEIVER_SCOPE);
        payload.put("publishedAt", asRfc3339(occurredAt));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", HOMEWORK_PUBLISHED);
        envelope.put("payloadVersion", 2);
        envelope.put("aggregateType", "assessment-homework");
        envelope.put("aggregateId", String.valueOf(homework.id()));
        // A homework changes from DRAFT to PUBLISHED at most once.  Its first
        // externally visible aggregate event is therefore version 1.
        envelope.put("aggregateVersion", 1);
        envelope.put("occurredAt", asRfc3339(occurredAt));
        envelope.put("correlationId", correlationId);
        envelope.put("payload", payload);

        jdbcTemplate.update("""
                        INSERT INTO assessment_event_outbox
                        (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
                         correlation_id, payload_json, routing_key, delivery_status, attempt_count,
                         next_attempt_at, lease_owner, lease_until, last_error, published_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, NULL, NULL, NULL, NULL, ?, ?)
                        """,
                eventId,
                HOMEWORK_PUBLISHED,
                2,
                "assessment-homework",
                String.valueOf(homework.id()),
                1,
                correlationId,
                serialize(envelope),
                routingKey(HOMEWORK_PUBLISHED),
                occurredAt,
                occurredAt,
                occurredAt
        );
    }

    public List<OutboxRecord> claimDue(String leaseOwner, Instant now, Duration leaseDuration, int limit) {
        List<Long> candidateIds = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM assessment_event_outbox
                        WHERE (delivery_status IN ('PENDING', 'RETRY')
                               AND next_attempt_at <= ?
                               AND (lease_until IS NULL OR lease_until < ?))
                           OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?)
                        ORDER BY next_attempt_at, id
                        LIMIT ?
                        """, Long.class, now, now, now, Math.max(1, limit));
        Instant leaseUntil = now.plus(leaseDuration);
        for (Long id : candidateIds) {
            jdbcTemplate.update("""
                            UPDATE assessment_event_outbox
                            SET delivery_status = 'IN_FLIGHT', lease_owner = ?, lease_until = ?, updated_at = ?
                            WHERE id = ?
                              AND ((delivery_status IN ('PENDING', 'RETRY')
                                    AND next_attempt_at <= ?
                                    AND (lease_until IS NULL OR lease_until < ?))
                                   OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?))
                            """, leaseOwner, leaseUntil, now, id, now, now, now);
        }
        return jdbcTemplate.query("""
                        SELECT id, event_id, event_type, payload_version, aggregate_type, aggregate_id,
                               aggregate_version, correlation_id, payload_json, routing_key, attempt_count, next_attempt_at
                        FROM assessment_event_outbox
                        WHERE lease_owner = ? AND delivery_status = 'IN_FLIGHT' AND lease_until = ?
                        ORDER BY id
                        """, (resultSet, rowNum) -> new OutboxRecord(
                resultSet.getLong("id"),
                deserialize(
                        resultSet.getString("event_id"),
                        resultSet.getString("event_type"),
                        resultSet.getInt("payload_version"),
                        resultSet.getString("aggregate_type"),
                        resultSet.getString("aggregate_id"),
                        resultSet.getLong("aggregate_version"),
                        resultSet.getString("correlation_id"),
                        resultSet.getString("payload_json")
                ),
                resultSet.getString("routing_key"),
                resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("next_attempt_at").toInstant()
        ), leaseOwner, leaseUntil);
    }

    public void markPublished(long id, String leaseOwner, Instant publishedAt) {
        jdbcTemplate.update("""
                        UPDATE assessment_event_outbox
                        SET delivery_status = 'PUBLISHED', published_at = ?, lease_owner = NULL, lease_until = NULL,
                            updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, publishedAt, publishedAt, id, leaseOwner, publishedAt);
    }

    public void markFailedAttempt(
            long id,
            String leaseOwner,
            int attemptCount,
            Instant nextAttemptAt,
            boolean terminal,
            String error,
            Instant updatedAt
    ) {
        jdbcTemplate.update("""
                        UPDATE assessment_event_outbox
                        SET delivery_status = ?, attempt_count = ?, next_attempt_at = ?, last_error = ?,
                            lease_owner = NULL, lease_until = NULL, updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, terminal ? "FAILED" : "RETRY", attemptCount, nextAttemptAt, error, updatedAt, id, leaseOwner, updatedAt);
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assessment_event_outbox WHERE delivery_status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    /**
     * The oldest message still eligible for automatic delivery. FAILED records
     * are counted separately: they require an operator decision instead of an
     * invisible retry loop.
     */
    public java.util.Optional<OutstandingEvent> oldestAutomaticallyDeliverable() {
        return jdbcTemplate.query("""
                        SELECT event_id, correlation_id, created_at
                        FROM assessment_event_outbox
                        WHERE delivery_status IN ('PENDING', 'RETRY')
                        ORDER BY created_at, id
                        LIMIT 1
                        """, (rs, rowNum) -> new OutstandingEvent(
                rs.getString("event_id"),
                rs.getString("correlation_id"),
                rs.getTimestamp("created_at").toInstant()
        )).stream().findFirst();
    }

    public record OutstandingEvent(String eventId, String correlationId, Instant createdAt) {
    }

    private ReliableEventEnvelope deserialize(
            String eventId,
            String eventType,
            int payloadVersion,
            String aggregateType,
            String aggregateId,
            long aggregateVersion,
            String correlationId,
            String json
    ) {
        try {
            var node = objectMapper.readTree(json);
            ReliableEventEnvelope envelope = new ReliableEventEnvelope(
                    eventId,
                    eventType,
                    payloadVersion,
                    aggregateType,
                    aggregateId,
                    aggregateVersion,
                    Instant.parse(node.required("occurredAt").asText()),
                    correlationId,
                    node.required("payload")
            );
            envelope.requireV2();
            return envelope;
        } catch (Exception exception) {
            throw new IllegalStateException("stored assessment outbox envelope is invalid", exception);
        }
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize v2 event envelope", ex);
        }
    }

    private String asRfc3339(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }

    private String routingKey(String eventType) {
        return "onlinejudge." + eventType;
    }
}
