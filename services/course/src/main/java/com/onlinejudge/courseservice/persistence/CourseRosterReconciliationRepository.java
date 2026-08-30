package com.onlinejudge.courseservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * #337's durable Course-owned repair gate.  It stores the published snapshot
 * identity, its roster watermark, and the next due time; Learning never
 * creates or synchronously requests these source facts.
 */
@Repository
public class CourseRosterReconciliationRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseRosterReconciliationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasEmitted(long courseId, long rosterVersion) {
        Long emitted = jdbcTemplate.query("SELECT snapshot_version FROM course_membership_reconciliation_checkpoint WHERE course_id = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null, courseId);
        return emitted != null && emitted >= rosterVersion;
    }

    /** Atomically reserves one due repair so two Course processes cannot emit the same next roster. */
    public boolean claimDue(long courseId, Instant now, Instant nextDue) {
        return jdbcTemplate.update("""
                UPDATE course_membership_reconciliation_checkpoint
                   SET next_reconcile_at = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND next_reconcile_at <= ?
                """, Timestamp.from(nextDue), courseId, Timestamp.from(now)) == 1;
    }

    public void record(long courseId, String snapshotEventId, long rosterVersion, Instant nextDue) {
        int updated = jdbcTemplate.update("""
                UPDATE course_membership_reconciliation_checkpoint
                   SET snapshot_event_id = ?, snapshot_version = ?, next_reconcile_at = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ?
                """, snapshotEventId, rosterVersion, Timestamp.from(nextDue), courseId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO course_membership_reconciliation_checkpoint
                        (course_id, snapshot_event_id, snapshot_version, next_reconcile_at)
                    VALUES (?, ?, ?, ?)
                    """, courseId, snapshotEventId, rosterVersion, Timestamp.from(nextDue));
        }
    }
}
