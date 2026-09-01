package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Durable, idempotent inbox and membership projection for #306 frozen facts.
 * The table names are the frozen Course-owned LRN tables; the consumer name
 * makes the (consumer, eventId) uniqueness explicit so different consumers can
 * each see every fact exactly once.
 */
@Repository
public class LrnEventInboxRepository {
    public static final String CONSUMER_NAME = "course-lrn";
    public static final String RECONCILIATION_CONSUMER = "notification-reconciliation";

    private final JdbcTemplate jdbc;

    public LrnEventInboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Returns false when the (consumer, eventId) was already applied. */
    public boolean record(String eventId, String eventType, String aggregateType, String aggregateId,
                          long aggregateVersion, String correlationId) {
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning_event_inbox
                 WHERE consumer_name = ? AND event_id = ?
                """, Integer.class, CONSUMER_NAME, eventId);
        if (existing != null && existing > 0) return false;
        jdbc.update("""
                INSERT INTO learning_event_inbox
                    (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                     correlation_id, processing_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'APPLIED')
                """, CONSUMER_NAME, eventId, eventType, aggregateType, aggregateId, aggregateVersion, correlationId);
        return true;
    }

    public Optional<Long> watermarkVersion(long courseId) {
        return jdbc.query("""
                SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?
                """, rs -> rs.next() ? Optional.of(rs.getLong("snapshot_version")) : Optional.empty(), courseId);
    }

    public void recordWatermark(long courseId, long snapshotVersion) {
        int updated = jdbc.update("""
                UPDATE learning_course_membership_watermark
                   SET snapshot_version = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ?
                """, snapshotVersion, courseId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO learning_course_membership_watermark (course_id, snapshot_version)
                    VALUES (?, ?)
                    """, courseId, snapshotVersion);
        }
    }

    public void upsertMember(long courseId, long userId, String membershipStatus, long memberVersion) {
        int updated = jdbc.update("""
                UPDATE learning_course_member_projection
                   SET membership_status = ?, member_version = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND user_id = ?
                """, membershipStatus, memberVersion, courseId, userId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO learning_course_member_projection
                        (course_id, user_id, membership_status, member_version)
                    VALUES (?, ?, ?, ?)
                    """, courseId, userId, membershipStatus, memberVersion);
        }
    }

    /** The snapshot is one atomic complete roster; replace the local projection from it. */
    public void replaceRoster(long courseId, List<MemberRow> members) {
        jdbc.update("DELETE FROM learning_course_member_projection WHERE course_id = ?", courseId);
        for (MemberRow member : members) {
            upsertMember(courseId, member.userId(), member.membershipStatus(), member.memberVersion());
        }
    }

    /** Receiver resolution authority for COURSE_ACTIVE_STUDENTS facts. */
    public List<Long> activeMemberUserIds(long courseId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT user_id FROM learning_course_member_projection
                 WHERE course_id = ? AND membership_status = 'ACTIVE' ORDER BY user_id
                """, Long.class, courseId);
        return ids == null ? List.of() : ids;
    }

    public boolean isActiveMember(long courseId, long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning_course_member_projection
                 WHERE course_id = ? AND user_id = ? AND membership_status = 'ACTIVE'
                """, Integer.class, courseId, userId);
        return count != null && count > 0;
    }

    /**
     * A version gap before the first complete roster watermark.  The fact is
     * retained in the inbox but no task/notification is fabricated; the open
     * reconciliation request is the durable, visible gap evidence.
     */
    public boolean recordGap(long courseId, long observedVersion, String triggeringEventId, String correlationId) {
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning_event_reconciliation_request
                 WHERE consumer_name = ? AND aggregate_type = 'course-membership-roster' AND aggregate_id = ?
                """, Integer.class, CONSUMER_NAME, String.valueOf(courseId));
        if (existing != null && existing > 0) return false;
        jdbc.update("""
                INSERT INTO learning_event_reconciliation_request
                    (consumer_name, aggregate_type, aggregate_id, observed_version, last_applied_version,
                     triggering_event_id, correlation_id, request_status)
                VALUES (?, 'course-membership-roster', ?, ?, 0, ?, ?, 'OPEN')
                """, CONSUMER_NAME, String.valueOf(courseId), observedVersion, triggeringEventId, correlationId);
        return true;
    }

    /** Durable internal reconciliation request; empty result means the key was already used. */
    public Optional<String> requestReconciliation(String sourceService, String eventId, String reason, String requestId) {
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning_event_reconciliation_request
                 WHERE consumer_name = ? AND aggregate_type = ? AND aggregate_id = ?
                """, Integer.class, RECONCILIATION_CONSUMER, sourceService, eventId);
        if (existing != null && existing > 0) return Optional.empty();
        jdbc.update("""
                INSERT INTO learning_event_reconciliation_request
                    (consumer_name, aggregate_type, aggregate_id, observed_version, last_applied_version,
                     triggering_event_id, correlation_id, request_status)
                VALUES (?, ?, ?, 1, 0, ?, ?, 'OPEN')
                """, RECONCILIATION_CONSUMER, sourceService, eventId, eventId, requestId);
        return Optional.of(requestId);
    }

    public record MemberRow(long userId, String membershipStatus, long memberVersion) { }
}
