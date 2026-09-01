package com.onlinejudge.gradeservice.service;

import com.onlinejudge.gradeservice.messaging.SourceGradeChangedEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

/** Applies at-least-once Assessment facts without requiring Assessment on the request path. */
@Service
public class SourceGradeProjectionService {
    private static final String CONSUMER = "grade-source-projection";
    private final JdbcTemplate jdbc;

    public SourceGradeProjectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ApplyResult apply(SourceGradeChangedEnvelope event) {
        if (inboxContains(event.eventId())) return new ApplyResult("DUPLICATE");

        long currentVersion = lockAggregate(event.aggregateId());
        // A concurrent delivery can pass the optimistic check above while the first
        // transaction is still uncommitted. Recheck after the aggregate lock is held.
        if (inboxContains(event.eventId())) return new ApplyResult("DUPLICATE");
        if (event.sourceVersion() > currentVersion + 1) {
            defer(event, currentVersion + 1);
            recordInbox(event, "DEFERRED");
            return new ApplyResult("GAP");
        }
        if (event.sourceVersion() <= currentVersion) {
            recordInbox(event, "IGNORED_STALE");
            return new ApplyResult("STALE");
        }

        applyInOrder(event);
        drainDeferred(event.aggregateId(), event.sourceVersion());
        refreshGap(event.aggregateId());
        return new ApplyResult("APPLIED");
    }

    /** Applies one fact from Assessment's immutable, versioned rebuild snapshot. */
    @Transactional
    public ApplyResult reconcileSnapshot(SourceGradeChangedEnvelope snapshot) {
        long currentVersion = lockAggregate(snapshot.aggregateId());
        if (snapshot.sourceVersion() < currentVersion) return new ApplyResult("STALE");
        applyProjection(snapshot);
        jdbc.update("UPDATE grade_source_projection_watermark SET current_version=? WHERE aggregate_id=?",
                snapshot.sourceVersion(), snapshot.aggregateId());
        jdbc.update("DELETE FROM grade_source_deferred_event WHERE aggregate_id=? AND source_version<=?",
                snapshot.aggregateId(), snapshot.sourceVersion());
        jdbc.update("DELETE FROM grade_source_projection_gap WHERE aggregate_id=?", snapshot.aggregateId());
        jdbc.update("""
                UPDATE grade_source_reconciliation_request
                   SET request_status='RESOLVED', resolved_at=CURRENT_TIMESTAMP
                 WHERE aggregate_id=? AND request_status='PENDING'
                """, snapshot.aggregateId());
        return new ApplyResult("RECONCILED");
    }

    private boolean inboxContains(String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM grade_event_inbox WHERE consumer_name=? AND event_id=?",
                Integer.class, CONSUMER, eventId);
        return count != null && count > 0;
    }

    private long lockAggregate(String aggregateId) {
        jdbc.update("""
                INSERT INTO grade_source_projection_watermark (aggregate_id, current_version)
                VALUES (?, 0)
                ON DUPLICATE KEY UPDATE aggregate_id=VALUES(aggregate_id)
                """, aggregateId);
        return jdbc.queryForObject(
                "SELECT current_version FROM grade_source_projection_watermark WHERE aggregate_id=? FOR UPDATE",
                Long.class, aggregateId);
    }

    private void defer(SourceGradeChangedEnvelope event, long expectedVersion) {
        jdbc.update("""
                INSERT INTO grade_source_deferred_event
                    (event_id, aggregate_id, aggregate_version, occurred_at, correlation_id,
                     course_id, source_type, source_id, student_id, score, full_score, source_status, source_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE aggregate_id=VALUES(aggregate_id)
                """, event.eventId(), event.aggregateId(), event.aggregateVersion(), Timestamp.from(event.occurredAt()),
                event.correlationId(), event.courseId(), event.sourceType(), event.sourceId(), event.studentId(),
                event.score(), event.fullScore(), event.status(), event.sourceVersion());
        jdbc.update("""
                INSERT INTO grade_source_projection_gap
                    (aggregate_id, expected_version, observed_version, correlation_id, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE expected_version=VALUES(expected_version),
                    observed_version=GREATEST(observed_version, VALUES(observed_version)),
                    correlation_id=VALUES(correlation_id), updated_at=CURRENT_TIMESTAMP
                """, event.aggregateId(), expectedVersion, event.sourceVersion(), event.correlationId());
        jdbc.update("""
                INSERT INTO grade_source_reconciliation_request
                    (aggregate_id, expected_version, observed_version, correlation_id, request_status, requested_at, resolved_at)
                VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, NULL)
                ON DUPLICATE KEY UPDATE expected_version=VALUES(expected_version),
                    observed_version=GREATEST(observed_version, VALUES(observed_version)),
                    correlation_id=VALUES(correlation_id), request_status='PENDING', requested_at=CURRENT_TIMESTAMP, resolved_at=NULL
                """, event.aggregateId(), expectedVersion, event.sourceVersion(), event.correlationId());
    }

    private void applyInOrder(SourceGradeChangedEnvelope event) {
        applyProjection(event);
        jdbc.update("UPDATE grade_source_projection_watermark SET current_version=? WHERE aggregate_id=?",
                event.sourceVersion(), event.aggregateId());
        recordInbox(event, "APPLIED");
    }

    private void applyProjection(SourceGradeChangedEnvelope event) {
        jdbc.update("""
                INSERT INTO grade_source_projection
                    (aggregate_id, course_id, source_type, source_id, student_id, score, full_score,
                     source_status, source_version, occurred_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), source_type=VALUES(source_type),
                    source_id=VALUES(source_id), student_id=VALUES(student_id), score=VALUES(score),
                    full_score=VALUES(full_score), source_status=VALUES(source_status),
                    source_version=VALUES(source_version), occurred_at=VALUES(occurred_at), updated_at=CURRENT_TIMESTAMP
                """, event.aggregateId(), event.courseId(), event.sourceType(), event.sourceId(), event.studentId(),
                event.score(), event.fullScore(), event.status(), event.sourceVersion(), Timestamp.from(event.occurredAt()));
    }

    private void drainDeferred(String aggregateId, long currentVersion) {
        long nextVersion = currentVersion + 1;
        while (true) {
            var deferred = jdbc.query("""
                            SELECT event_id, aggregate_id, aggregate_version, occurred_at, correlation_id,
                                   course_id, source_type, source_id, student_id, score, full_score,
                                   source_status, source_version
                              FROM grade_source_deferred_event
                             WHERE aggregate_id=? AND source_version=?
                            """,
                    (rs, ignored) -> new SourceGradeChangedEnvelope(
                            rs.getString("event_id"), rs.getString("aggregate_id"), rs.getLong("aggregate_version"),
                            rs.getTimestamp("occurred_at").toInstant(), rs.getString("correlation_id"),
                            rs.getString("course_id"), rs.getString("source_type"), rs.getString("source_id"),
                            rs.getString("student_id"), rs.getBigDecimal("score"), rs.getBigDecimal("full_score"),
                            rs.getString("source_status"), rs.getLong("source_version")),
                    aggregateId, nextVersion).stream().findFirst();
            if (deferred.isEmpty()) return;

            SourceGradeChangedEnvelope next = deferred.orElseThrow();
            applyInOrder(next);
            jdbc.update("DELETE FROM grade_source_deferred_event WHERE aggregate_id=? AND source_version=?",
                    aggregateId, nextVersion);
            jdbc.update("""
                    UPDATE grade_event_inbox SET processing_status='APPLIED', processed_at=CURRENT_TIMESTAMP
                     WHERE consumer_name=? AND aggregate_id=? AND aggregate_version=?
                    """, CONSUMER, aggregateId, nextVersion);
            nextVersion++;
        }
    }

    private void refreshGap(String aggregateId) {
        Long current = jdbc.queryForObject(
                "SELECT current_version FROM grade_source_projection_watermark WHERE aggregate_id=?",
                Long.class, aggregateId);
        Long nextDeferred = jdbc.queryForObject(
                "SELECT MIN(source_version) FROM grade_source_deferred_event WHERE aggregate_id=?",
                Long.class, aggregateId);
        if (nextDeferred == null) {
            jdbc.update("DELETE FROM grade_source_projection_gap WHERE aggregate_id=?", aggregateId);
            jdbc.update("""
                    UPDATE grade_source_reconciliation_request
                       SET request_status='RESOLVED', resolved_at=CURRENT_TIMESTAMP
                     WHERE aggregate_id=? AND request_status='PENDING' AND observed_version <= ?
                    """, aggregateId, current);
            return;
        }
        jdbc.update("""
                UPDATE grade_source_projection_gap
                   SET expected_version=?, observed_version=?, updated_at=CURRENT_TIMESTAMP
                 WHERE aggregate_id=?
                """, current + 1, nextDeferred, aggregateId);
    }

    private void recordInbox(SourceGradeChangedEnvelope event, String status) {
        jdbc.update("""
                INSERT INTO grade_event_inbox
                    (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                     correlation_id, processing_status, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)
                """, CONSUMER, event.eventId(), SourceGradeChangedEnvelope.EVENT_TYPE,
                SourceGradeChangedEnvelope.AGGREGATE_TYPE, event.aggregateId(), event.aggregateVersion(),
                event.correlationId(), status);
    }

    public record ApplyResult(String decision) { }
}
