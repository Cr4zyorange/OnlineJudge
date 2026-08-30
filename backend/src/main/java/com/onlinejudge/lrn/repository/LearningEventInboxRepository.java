package com.onlinejudge.lrn.repository;

import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class LearningEventInboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public LearningEventInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasEvent(String consumerName, String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_event_inbox WHERE consumer_name = ? AND event_id = ?",
                Integer.class, consumerName, eventId);
        return count != null && count > 0;
    }

    public boolean record(String consumerName, ReliableEventEnvelope envelope, String status, Instant processedAt) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO learning_event_inbox
                            (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                             correlation_id, processing_status, processed_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, consumerName, envelope.eventId(), envelope.eventType(), envelope.aggregateType(),
                    envelope.aggregateId(), envelope.aggregateVersion(), envelope.correlationId(), status, processedAt);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public long lastAppliedAggregateVersion(String consumerName, String aggregateType, String aggregateId) {
        Long version = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(aggregate_version), 0)
                        FROM learning_event_inbox
                        WHERE consumer_name = ? AND aggregate_type = ? AND aggregate_id = ?
                          AND processing_status = 'APPLIED'
                        """, Long.class, consumerName, aggregateType, aggregateId);
        return version == null ? 0L : version;
    }

    public int recordAttempt(String consumerName, String eventId, String error, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE learning_event_delivery_attempt
                        SET attempt_count = attempt_count + 1, last_error = ?, updated_at = ?
                        WHERE consumer_name = ? AND event_id = ?
                        """, error, now, consumerName, eventId);
        if (updated == 0) {
            try {
                jdbcTemplate.update("""
                                INSERT INTO learning_event_delivery_attempt
                                (consumer_name, event_id, attempt_count, last_error, updated_at)
                                VALUES (?, ?, 1, ?, ?)
                                """, consumerName, eventId, error, now);
            } catch (DuplicateKeyException ignored) {
                jdbcTemplate.update("""
                                UPDATE learning_event_delivery_attempt
                                SET attempt_count = attempt_count + 1, last_error = ?, updated_at = ?
                                WHERE consumer_name = ? AND event_id = ?
                                """, error, now, consumerName, eventId);
            }
        }
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT attempt_count FROM learning_event_delivery_attempt
                        WHERE consumer_name = ? AND event_id = ?
                        """, Integer.class, consumerName, eventId);
        return count == null ? 0 : count;
    }
}
