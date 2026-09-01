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
                 WHERE course_id = ? AND snapshot_version < ?
                """, snapshotVersion, courseId, snapshotVersion);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO learning_course_membership_watermark (course_id, snapshot_version)
                    SELECT ?, ?
                     WHERE NOT EXISTS (SELECT 1 FROM learning_course_membership_watermark WHERE course_id = ?)
                    """, courseId, snapshotVersion, courseId);
        }
    }

    /**
     * Monotonic projection for one incremental course.member.changed.v2 fact:
     * only a higher memberVersion advances, an older version is a no-op, and a
     * forward jump (missing intermediate version) is recorded as an auditable
     * reconciliation request.  Course's next authoritative roster snapshot
     * covers and closes that gap.
     */
    public void upsertMember(long courseId, long userId, String membershipStatus, long memberVersion,
                             String eventId, String correlationId) {
        Long current = currentMemberVersion(courseId, userId);
        if (current != null && memberVersion <= current) return;
        long lastApplied = current == null ? 0 : current;
        if (memberVersion > lastApplied + 1) {
            recordMemberGap(courseId, userId, lastApplied, memberVersion, eventId, correlationId);
        }
        int updated = jdbc.update("""
                UPDATE learning_course_member_projection
                   SET membership_status = ?, member_version = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND user_id = ? AND member_version < ?
                """, membershipStatus, memberVersion, courseId, userId, memberVersion);
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
            jdbc.update("""
                    INSERT INTO learning_course_member_projection
                        (course_id, user_id, membership_status, member_version)
                    VALUES (?, ?, ?, ?)
                    """, courseId, member.userId(), member.membershipStatus(), member.memberVersion());
        }
    }

    private Long currentMemberVersion(long courseId, long userId) {
        return jdbc.query("""
                SELECT member_version FROM learning_course_member_projection WHERE course_id = ? AND user_id = ?
                """, rs -> rs.next() ? rs.getLong(1) : null, courseId, userId);
    }

    private void recordMemberGap(long courseId, long userId, long lastAppliedVersion, long observedVersion,
                                 String eventId, String correlationId) {
        String aggregateId = courseId + ":" + userId;
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning_event_reconciliation_request
                 WHERE consumer_name = ? AND aggregate_type = 'course-member' AND aggregate_id = ?
                   AND observed_version = ?
                """, Integer.class, CONSUMER_NAME, aggregateId, observedVersion);
        if (existing != null && existing > 0) return;
        jdbc.update("""
                INSERT INTO learning_event_reconciliation_request
                    (consumer_name, aggregate_type, aggregate_id, observed_version, last_applied_version,
                     triggering_event_id, correlation_id, request_status)
                VALUES (?, 'course-member', ?, ?, ?, ?, ?, 'OPEN')
                """, CONSUMER_NAME, aggregateId, observedVersion, lastAppliedVersion, eventId, correlationId);
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

    /**
     * Persists the original envelope durably in the same transaction as the
     * inbox and the reconciliation gap, so a watermark-missing fact is never
     * ACKed and forgotten.  False when the event was already deferred: broker
     * redelivery never duplicates the state-machine row.
     */
    public boolean deferEvent(String eventId, String eventType, String aggregateType, String aggregateId,
                              long aggregateVersion, String correlationId, String envelopeJson) {
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning_deferred_event WHERE consumer_name = ? AND event_id = ?
                """, Integer.class, CONSUMER_NAME, eventId);
        if (existing != null && existing > 0) return false;
        jdbc.update("""
                INSERT INTO learning_deferred_event
                    (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                     correlation_id, envelope_json, deferral_reason, delivery_status, attempt_count,
                     next_attempt_at, lease_owner, lease_until, last_error, resolved_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MEMBERSHIP_PROJECTION_PENDING', 'PENDING', 0,
                        CURRENT_TIMESTAMP, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, CONSUMER_NAME, eventId, eventType, aggregateType, aggregateId, aggregateVersion,
                correlationId, envelopeJson);
        return true;
    }

    /** Pending deferred envelopes for this consumer, oldest first. */
    public List<DeferredEvent> pendingDeferredEvents() {
        return jdbc.query("""
                SELECT event_id, event_type, correlation_id, envelope_json
                  FROM learning_deferred_event
                 WHERE consumer_name = ? AND delivery_status = 'PENDING'
                 ORDER BY id
                """, (rs, rowNum) -> new DeferredEvent(
                rs.getString("event_id"), rs.getString("event_type"),
                rs.getString("correlation_id"), rs.getString("envelope_json")), CONSUMER_NAME);
    }

    /** Terminal exactly-once mark for a replayed deferred envelope. */
    public boolean markDeferredResolved(String eventId) {
        return jdbc.update("""
                UPDATE learning_deferred_event
                   SET delivery_status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE consumer_name = ? AND event_id = ? AND delivery_status = 'PENDING'
                """, CONSUMER_NAME, eventId) == 1;
    }

    /** Closes the OPEN complete-roster gap once the deferred facts are replayed. */
    public void resolveRosterGap(long courseId) {
        jdbc.update("""
                UPDATE learning_event_reconciliation_request
                   SET request_status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP
                 WHERE consumer_name = ? AND aggregate_type = 'course-membership-roster'
                   AND aggregate_id = ? AND request_status = 'OPEN'
                """, CONSUMER_NAME, String.valueOf(courseId));
    }

    /** The authoritative snapshot covers a member's missing versions; close the reconciliation evidence. */
    public void resolveMemberGap(long courseId, long userId, long coveredVersion) {
        jdbc.update("""
                UPDATE learning_event_reconciliation_request
                   SET request_status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP
                 WHERE consumer_name = ? AND aggregate_type = 'course-member' AND aggregate_id = ?
                   AND observed_version <= ? AND request_status = 'OPEN'
                """, CONSUMER_NAME, courseId + ":" + userId, coveredVersion);
    }

    public record MemberRow(long userId, String membershipStatus, long memberVersion) { }

    public record DeferredEvent(String eventId, String eventType, String correlationId, String envelopeJson) { }
}
