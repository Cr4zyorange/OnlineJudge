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

    public boolean hasObservedCourse(long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_course_member_projection WHERE course_id = ?", Integer.class, courseId);
        return count != null && count > 0;
    }
}
