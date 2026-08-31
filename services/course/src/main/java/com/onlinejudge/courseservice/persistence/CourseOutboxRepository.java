package com.onlinejudge.courseservice.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    public String append(String eventType, String aggregateType, String aggregateId, long aggregateVersion,
                         String correlationId, Map<String, Object> payload) {
        try {
            String eventId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO course_event_outbox
                    (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
                     correlation_id, payload_json, routing_key, delivery_status, attempt_count, next_attempt_at)
                    VALUES (?, ?, 2, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)
                    """, eventId, eventType, aggregateType, aggregateId, aggregateVersion,
                    correlationId, objectMapper.writeValueAsString(payload), "onlinejudge." + eventType);
            return eventId;
        } catch (Exception exception) {
            throw new IllegalStateException("course outbox write failed", exception);
        }
    }

    /** Only a successfully published authoritative roster can become due for a source-owned repair. */
    public List<Long> publishedRosterCourseIds() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT aggregate_id FROM course_event_outbox
                 WHERE event_type = 'course.membership.snapshot.v2'
                   AND aggregate_type = 'course-membership-roster'
                   AND delivery_status = 'PUBLISHED'
                 ORDER BY aggregate_id
                """, String.class).stream().map(Long::parseLong).toList();
    }

    /**
     * Each row is claimed by a conditional update, rather than by trusting a
     * prior select.  That preserves single publisher ownership when relays in
     * different Course processes observe the same due candidate.
     */
    @Transactional
    public List<OutboxRecord> claimDue(String leaseOwner, Instant now, Duration leaseDuration, int limit) {
        Instant leaseUntil = now.plus(leaseDuration);
        List<Long> candidates = jdbcTemplate.queryForList("""
                SELECT id FROM course_event_outbox
                 WHERE (delivery_status IN ('PENDING', 'RETRY')
                        AND next_attempt_at <= ?
                        AND (lease_until IS NULL OR lease_until < ?))
                    OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?)
                 ORDER BY next_attempt_at, id
                 LIMIT ?
                """, Long.class, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Math.max(1, limit));
        List<OutboxRecord> claimed = new ArrayList<>();
        for (long id : candidates) {
            int updated = jdbcTemplate.update("""
                    UPDATE course_event_outbox
                       SET delivery_status = 'IN_FLIGHT', lease_owner = ?, lease_until = ?,
                           lease_generation = lease_generation + 1, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                       AND ((delivery_status IN ('PENDING', 'RETRY')
                             AND next_attempt_at <= ?
                             AND (lease_until IS NULL OR lease_until < ?))
                            OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?))
                    """, leaseOwner, Timestamp.from(leaseUntil), id, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            if (updated == 1) {
                claimed.add(readClaim(id, leaseOwner));
            }
        }
        return claimed;
    }

    /** Returns zero when a fenced, expired, or superseded owner tries to acknowledge a newer lease. */
    public int markPublished(OutboxRecord record, Instant publishedAt) {
        return jdbcTemplate.update("""
                UPDATE course_event_outbox
                   SET delivery_status = 'PUBLISHED', published_at = ?, lease_owner = NULL, lease_until = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ?
                   AND lease_generation = ? AND lease_until >= ?
                """, Timestamp.from(publishedAt), record.id(), record.leaseOwner(), record.leaseGeneration(), Timestamp.from(publishedAt));
    }

    /** Applies bounded exponential retry only while this exact owner/generation lease remains current. */
    public DeliveryUpdate markFailedAttempt(OutboxRecord record, Instant now, String error, int maxAttempts,
                                             Duration retryBase, Duration retryMaximum) {
        int attempt = record.attemptCount() + 1;
        boolean terminal = attempt >= Math.max(1, maxAttempts);
        Instant nextAttempt = terminal ? now : now.plus(backoff(attempt, retryBase, retryMaximum));
        int updated = jdbcTemplate.update("""
                UPDATE course_event_outbox
                   SET delivery_status = ?, attempt_count = ?, next_attempt_at = ?, last_error = ?,
                       lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ?
                   AND lease_generation = ? AND lease_until >= ?
                """, terminal ? "FAILED" : "RETRY", attempt, Timestamp.from(nextAttempt), safeError(error),
                record.id(), record.leaseOwner(), record.leaseGeneration(), Timestamp.from(now));
        if (updated == 0) return DeliveryUpdate.STALE;
        return terminal ? DeliveryUpdate.FAILED : DeliveryUpdate.RETRY;
    }

    /** FAILED is terminal until an operator consciously puts the durable fact back into the retry queue. */
    public boolean recoverFailed(long id, String incidentReference, Instant now) {
        String reference = incidentReference == null || incidentReference.isBlank() ? "operator-recovery" : incidentReference.trim();
        return jdbcTemplate.update("""
                UPDATE course_event_outbox
                   SET delivery_status = 'PENDING', attempt_count = 0, next_attempt_at = ?, lease_owner = NULL, lease_until = NULL,
                       last_error = CONCAT('RECOVERED[', ?, '] ', COALESCE(last_error, '')), updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND delivery_status = 'FAILED'
                """, Timestamp.from(now), reference.length() > 128 ? reference.substring(0, 128) : reference, id) == 1;
    }

    private OutboxRecord readClaim(long id, String leaseOwner) {
        return jdbcTemplate.query("""
                SELECT id, event_id, event_type, aggregate_type, aggregate_id, aggregate_version, correlation_id,
                       payload_json, routing_key, attempt_count, lease_owner, lease_generation, lease_until
                  FROM course_event_outbox
                 WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Course outbox lease was lost before it could be read");
            Timestamp leaseUntil = rs.getTimestamp("lease_until");
            return new OutboxRecord(rs.getLong("id"), rs.getString("event_id"), rs.getString("event_type"),
                    rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getLong("aggregate_version"),
                    rs.getString("correlation_id"), rs.getString("payload_json"), rs.getString("routing_key"),
                    rs.getInt("attempt_count"), rs.getString("lease_owner"), rs.getLong("lease_generation"), leaseUntil.toInstant());
        }, id, leaseOwner);
    }

    private Duration backoff(int attempt, Duration retryBase, Duration retryMaximum) {
        long base = Math.max(1, retryBase.getSeconds());
        long maximum = Math.max(base, retryMaximum.getSeconds());
        long multiplier = 1L << Math.min(20, Math.max(0, attempt - 1));
        return Duration.ofSeconds(base >= maximum / multiplier ? maximum : base * multiplier);
    }

    private String safeError(String error) {
        String value = error == null || error.isBlank() ? "Course outbox publication failed" : error;
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    public enum DeliveryUpdate { RETRY, FAILED, STALE }

    public record OutboxRecord(long id, String eventId, String eventType, String aggregateType, String aggregateId,
                               long aggregateVersion, String correlationId, String payloadJson, String routingKey,
                               int attemptCount, String leaseOwner, long leaseGeneration, Instant leaseUntil) { }
}
