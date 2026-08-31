package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseMemberProjectionRepository {
    private final JdbcTemplate jdbc;
    public CourseMemberProjectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean isActive(String courseId, String userId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_course_member_projection WHERE course_id = ? AND user_id = ? AND membership_status = 'ACTIVE'", Integer.class, courseId, userId); return count != null && count == 1; }
}
