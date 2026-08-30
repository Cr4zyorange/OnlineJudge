package com.onlinejudge.courseservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable Course-owned marker: only a newer roster needs another reconciliation snapshot. */
@Repository
public class CourseRosterReconciliationRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseRosterReconciliationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasEmitted(long courseId, long rosterVersion) {
        Long emitted = jdbcTemplate.query("SELECT emitted_roster_version FROM course_roster_reconciliation_checkpoint WHERE course_id = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null, courseId);
        return emitted != null && emitted >= rosterVersion;
    }

    public void record(long courseId, long rosterVersion) {
        int updated = jdbcTemplate.update("UPDATE course_roster_reconciliation_checkpoint SET emitted_roster_version = ?, updated_at = CURRENT_TIMESTAMP WHERE course_id = ?",
                rosterVersion, courseId);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO course_roster_reconciliation_checkpoint (course_id, emitted_roster_version) VALUES (?, ?)", courseId, rosterVersion);
        }
    }
}
