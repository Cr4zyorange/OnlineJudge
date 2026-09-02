package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseMemberProjectionRepository {
    private final JdbcTemplate jdbc;
    public CourseMemberProjectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean isActive(String courseId, String userId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_course_member_projection WHERE course_id = ? AND user_id = ? AND membership_status = 'ACTIVE'", Integer.class, courseId, userId); return count != null && count == 1; }

    /**
     * A local member decision is authoritative when the member itself has no durable gap and
     * either we have a concrete local row or the course has already consumed a complete roster snapshot.
     */
    public boolean isAuthoritativeFor(String courseId, String userId) {
        Integer gapCount = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_course_projection_gap WHERE course_id = ? AND user_id = ?", Integer.class, courseId, userId);
        if (gapCount != null && gapCount > 0) {
            return false;
        }
        Integer memberCount = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_course_member_projection WHERE course_id = ? AND user_id = ?", Integer.class, courseId, userId);
        if (memberCount != null && memberCount == 1) {
            return true;
        }
        Long watermark = jdbc.query("SELECT roster_version FROM assessment_course_membership_watermark WHERE course_id = ?",
                (rs, ignored) -> rs.getLong(1), courseId).stream().findFirst().orElse(0L);
        return watermark != null && watermark > 0;
    }
}
