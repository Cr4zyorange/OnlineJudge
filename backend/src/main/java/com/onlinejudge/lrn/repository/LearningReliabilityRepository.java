package com.onlinejudge.lrn.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class LearningReliabilityRepository {
    private final JdbcTemplate jdbcTemplate;

    public LearningReliabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deadLetter(
            String consumerName,
            String eventId,
            String eventType,
            String correlationId,
            String envelopeJson,
            String classification,
            String message,
            int attempts,
            Instant now
    ) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO learning_event_dead_letter
                            (consumer_name, event_id, event_type, correlation_id, envelope_json, failure_classification,
                             failure_message, attempt_count, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, consumerName, eventId, eventType, correlationId, envelopeJson, classification,
                    message, attempts, now, now);
        } catch (DuplicateKeyException ignored) {
            jdbcTemplate.update("""
                            UPDATE learning_event_dead_letter
                            SET failure_classification = ?, failure_message = ?, attempt_count = ?, updated_at = ?
                            WHERE consumer_name = ? AND event_id = ?
                            """, classification, message, attempts, now, consumerName, eventId);
        }
    }

    public void recordGap(
            String consumerName,
            String aggregateType,
            String aggregateId,
            long observedVersion,
            long lastAppliedVersion,
            String eventId,
            String correlationId,
            Instant now
    ) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO learning_event_reconciliation_request
                            (consumer_name, aggregate_type, aggregate_id, observed_version, last_applied_version,
                             triggering_event_id, correlation_id, request_status, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
                            """, consumerName, aggregateType, aggregateId, observedVersion, lastAppliedVersion,
                    eventId, correlationId, now);
        } catch (DuplicateKeyException ignored) {
            // Same observed gap is intentionally one auditable reconciliation request.
        }
    }

    public Optional<DeadLetter> findForReplay(String consumerName, String eventId) {
        return jdbcTemplate.query("""
                        SELECT event_type, correlation_id, envelope_json, attempt_count
                        FROM learning_event_dead_letter
                        WHERE consumer_name = ? AND event_id = ? AND replayed_at IS NULL
                        """, (rs, rowNum) -> new DeadLetter(
                eventId, rs.getString("event_type"), rs.getString("correlation_id"),
                rs.getString("envelope_json"), rs.getInt("attempt_count")
        ), consumerName, eventId).stream().findFirst();
    }

    public void markReplayed(String consumerName, String eventId, String operator, Instant now) {
        jdbcTemplate.update("""
                        UPDATE learning_event_dead_letter
                        SET replayed_at = ?, replayed_by = ?, updated_at = ?
                        WHERE consumer_name = ? AND event_id = ? AND replayed_at IS NULL
                        """, now, operator, now, consumerName, eventId);
    }

    public long deadLetterCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_event_dead_letter WHERE replayed_at IS NULL", Long.class);
        return count == null ? 0 : count;
    }

    public Optional<DeadLetterObservation> oldestUnreplayedDeadLetter() {
        return jdbcTemplate.query("""
                        SELECT event_id, correlation_id, created_at
                        FROM learning_event_dead_letter
                        WHERE replayed_at IS NULL
                        ORDER BY created_at, id
                        LIMIT 1
                        """, (rs, rowNum) -> new DeadLetterObservation(
                rs.getString("event_id"), rs.getString("correlation_id"), rs.getTimestamp("created_at").toInstant()
        )).stream().findFirst();
    }

    public record DeadLetter(String eventId, String eventType, String correlationId, String envelopeJson, int attemptCount) {
    }

    public record DeadLetterObservation(String eventId, String correlationId, Instant createdAt) {
    }
}
