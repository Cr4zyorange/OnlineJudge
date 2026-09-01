package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Durable, idempotent inbox for #306 frozen facts consumed by Course LRN. */
@Repository
public class LrnEventInboxRepository {
    private final JdbcTemplate jdbc;

    public LrnEventInboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Returns false when the eventId was already applied (duplicate delivery). */
    public boolean record(String eventId, String eventType, String aggregateType, String aggregateId,
                          long aggregateVersion, String payloadJson) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_learning_event_inbox WHERE event_id = ?", Integer.class, eventId);
        if (existing != null && existing > 0) return false;
        jdbc.update("""
                INSERT INTO course_learning_event_inbox
                    (event_id, event_type, aggregate_type, aggregate_id, aggregate_version, payload_json, status)
                VALUES (?, ?, ?, ?, ?, ?, 'APPLIED')
                """, eventId, eventType, aggregateType, aggregateId, aggregateVersion, payloadJson);
        return true;
    }

    public void recordWatermark(long courseId, long snapshotVersion) {
        int updated = jdbc.update("""
                UPDATE course_learning_membership_watermark
                   SET snapshot_version = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ?
                """, snapshotVersion, courseId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO course_learning_membership_watermark (course_id, snapshot_version)
                    VALUES (?, ?)
                    """, courseId, snapshotVersion);
        }
    }

    public Optional<Long> watermarkVersion(long courseId) {
        return jdbc.query("""
                SELECT snapshot_version FROM course_learning_membership_watermark WHERE course_id = ?
                """, rs -> rs.next() ? Optional.of(rs.getLong("snapshot_version")) : Optional.empty(), courseId);
    }
}
