package com.onlinejudge.lrn.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** Learning-owned projection populated from course.member.changed.v2. */
@Repository
public class LearningCourseMemberProjectionRepository {
    private final JdbcTemplate jdbcTemplate;

    public LearningCourseMemberProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(long courseId, long userId, String membershipStatus, long memberVersion, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE learning_course_member_projection
                        SET membership_status = ?, member_version = ?, updated_at = ?
                        WHERE course_id = ? AND user_id = ? AND member_version < ?
                        """, membershipStatus, memberVersion, updatedAt, courseId, userId, memberVersion);
        if (updated == 0) {
            try {
                jdbcTemplate.update("""
                                INSERT INTO learning_course_member_projection
                                (course_id, user_id, membership_status, member_version, updated_at)
                                VALUES (?, ?, ?, ?, ?)
                                """, courseId, userId, membershipStatus, memberVersion, updatedAt);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // Concurrent projection delivery either already applied this version or won the update.
            }
        }
    }

    public List<Long> activeStudentIds(long courseId) {
        return jdbcTemplate.queryForList("""
                        SELECT user_id
                        FROM learning_course_member_projection
                        WHERE course_id = ? AND membership_status = 'ACTIVE'
                        ORDER BY user_id
                        """, Long.class, courseId);
    }

    /**
     * A row for one member is not a roster.  Homework receiver resolution is
     * allowed only after Course has supplied one complete course-scoped
     * snapshot, whose aggregate version is persisted as the local watermark.
     */
    public boolean hasCompleteRoster(long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_course_membership_watermark WHERE course_id = ?", Integer.class, courseId);
        return count != null && count > 0;
    }

    public void replaceWithCompleteRoster(long courseId, long snapshotVersion, List<MemberSnapshot> members, Instant updatedAt) {
        int claimed = jdbcTemplate.update("""
                        UPDATE learning_course_membership_watermark
                        SET snapshot_version = ?, completed_at = ?, updated_at = ?
                        WHERE course_id = ? AND snapshot_version < ?
                        """, snapshotVersion, updatedAt, updatedAt, courseId, snapshotVersion);
        if (claimed == 0) {
            try {
                jdbcTemplate.update("""
                                INSERT INTO learning_course_membership_watermark
                                (course_id, snapshot_version, completed_at, updated_at)
                                VALUES (?, ?, ?, ?)
                                """, courseId, snapshotVersion, updatedAt, updatedAt);
                claimed = 1;
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // Another transaction created the waterline first. Claim the
                // newer snapshot if this one is still ahead of it.
                claimed = jdbcTemplate.update("""
                                UPDATE learning_course_membership_watermark
                                SET snapshot_version = ?, completed_at = ?, updated_at = ?
                                WHERE course_id = ? AND snapshot_version < ?
                                """, snapshotVersion, updatedAt, updatedAt, courseId, snapshotVersion);
            }
        }
        if (claimed == 0) return;

        // This repository is invoked inside the consumer's local transaction:
        // no reader can observe the new watermark until the replacement is
        // complete, so receiver resolution never sees a half-written roster.
        jdbcTemplate.update("DELETE FROM learning_course_member_projection WHERE course_id = ?", courseId);
        for (MemberSnapshot member : members) {
            jdbcTemplate.update("""
                            INSERT INTO learning_course_member_projection
                            (course_id, user_id, membership_status, member_version, updated_at)
                            VALUES (?, ?, ?, ?, ?)
                            """, courseId, member.userId(), member.membershipStatus(), member.memberVersion(), updatedAt);
        }
    }

    public record MemberSnapshot(long userId, String membershipStatus, long memberVersion) {
    }
}
