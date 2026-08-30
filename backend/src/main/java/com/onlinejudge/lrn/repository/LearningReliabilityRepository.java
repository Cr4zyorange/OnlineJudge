package com.onlinejudge.lrn.repository;

import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    public void defer(
            String consumerName,
            ReliableEventEnvelope envelope,
            String envelopeJson,
            String reason,
            Instant now
    ) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO learning_deferred_event
                            (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                             correlation_id, envelope_json, deferral_reason, delivery_status, attempt_count,
                             next_attempt_at, lease_owner, lease_until, last_error, resolved_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, NULL, NULL, NULL, NULL, ?, ?)
                            """, consumerName, envelope.eventId(), envelope.eventType(), envelope.aggregateType(),
                    envelope.aggregateId(), envelope.aggregateVersion(), envelope.correlationId(), envelopeJson,
                    reason, now, now, now);
        } catch (DuplicateKeyException ignored) {
            // The original broker delivery may be redelivered while the durable
            // reconciliation worker owns it. Keep the original envelope/state.
        }
    }

    public List<DeferredEvent> claimDeferred(String leaseOwner, Instant now, Duration leaseDuration, int limit) {
        List<Long> candidates = jdbcTemplate.queryForList("""
                        SELECT id FROM learning_deferred_event
                        WHERE (delivery_status = 'PENDING' AND next_attempt_at <= ?)
                           OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?)
                        ORDER BY next_attempt_at, id
                        LIMIT ?
                        """, Long.class, now, now, Math.max(1, limit));
        Instant leaseUntil = now.plus(leaseDuration);
        for (Long id : candidates) {
            jdbcTemplate.update("""
                            UPDATE learning_deferred_event
                            SET delivery_status = 'IN_FLIGHT', lease_owner = ?, lease_until = ?, updated_at = ?
                            WHERE id = ? AND ((delivery_status = 'PENDING' AND next_attempt_at <= ?)
                                               OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?))
                            """, leaseOwner, leaseUntil, now, id, now, now);
        }
        return jdbcTemplate.query("""
                        SELECT id, event_id, envelope_json
                        FROM learning_deferred_event
                        WHERE delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until = ?
                        ORDER BY id
                        """, (rs, rowNum) -> new DeferredEvent(
                rs.getLong("id"), rs.getString("event_id"), rs.getString("envelope_json")
        ), leaseOwner, leaseUntil);
    }

    public void markDeferredResolved(long id, String leaseOwner, Instant now) {
        jdbcTemplate.update("""
                        UPDATE learning_deferred_event
                        SET delivery_status = 'RESOLVED', resolved_at = ?, lease_owner = NULL, lease_until = NULL,
                            updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, now, now, id, leaseOwner, now);
    }

    public void releaseDeferred(long id, String leaseOwner, Instant nextAttemptAt, String error, Instant now) {
        jdbcTemplate.update("""
                        UPDATE learning_deferred_event
                        SET delivery_status = 'PENDING', attempt_count = attempt_count + 1, next_attempt_at = ?,
                            lease_owner = NULL, lease_until = NULL, last_error = ?, updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, nextAttemptAt, error, now, id, leaseOwner, now);
    }

    public void markDeferredFailed(long id, String leaseOwner, String error, Instant now) {
        jdbcTemplate.update("""
                        UPDATE learning_deferred_event
                        SET delivery_status = 'FAILED', lease_owner = NULL, lease_until = NULL,
                            last_error = ?, updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, error, now, id, leaseOwner, now);
    }

    public long deferredCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_deferred_event WHERE delivery_status <> 'RESOLVED'", Long.class);
        return count == null ? 0 : count;
    }

    public Optional<DeferredObservation> oldestUnresolvedDeferred() {
        return jdbcTemplate.query("""
                        SELECT event_id, correlation_id, created_at
                        FROM learning_deferred_event
                        WHERE delivery_status <> 'RESOLVED'
                        ORDER BY created_at, id
                        LIMIT 1
                        """, (rs, rowNum) -> new DeferredObservation(
                rs.getString("event_id"), rs.getString("correlation_id"), rs.getTimestamp("created_at").toInstant()
        )).stream().findFirst();
    }

    public void resolveReconciliationForEvent(String eventId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE learning_event_reconciliation_request
                        SET request_status = 'RESOLVED', resolved_at = ?
                        WHERE triggering_event_id = ? AND request_status = 'OPEN'
                        """, now, eventId);
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

    public record DeferredEvent(long id, String eventId, String envelopeJson) {
    }

    public record DeferredObservation(String eventId, String correlationId, Instant createdAt) {
    }
}
