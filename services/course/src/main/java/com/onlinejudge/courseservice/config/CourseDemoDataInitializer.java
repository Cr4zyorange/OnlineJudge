package com.onlinejudge.courseservice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Issue #396: seeds the demo course on a clean Course database when demo data is enabled.
 *
 * <p>The fixed identifiers follow the monolith demo convention (course 9501, LABs 950201/950211 in
 * the Assessment service). User ids assume a clean deployment where identity's
 * AuthSeedDataInitializer inserted student001=1 and teacher001=2 first. The table-level guard plus
 * ON DUPLICATE KEY UPDATE keeps restarts and concurrent boots idempotent.
 */
@Component
@ConditionalOnProperty(name = "onlinejudge.demo-data.enabled", havingValue = "true", matchIfMissing = false)
public class CourseDemoDataInitializer implements ApplicationRunner {

    static final long DEMO_COURSE_ID = 9501L;
    static final long DEMO_TEACHER_USER_ID = 2L;
    static final long DEMO_STUDENT_USER_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    public CourseDemoDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    public void seedIfEmpty() {
        Integer existingCourses = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course", Integer.class);
        if (existingCourses == null || existingCourses > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO crs_course
                  (id, course_name, description, teacher_id, semester, category, enrollment_mode, status)
                VALUES (?, '软件工程实训演示课程',
                        '用于答辩与本地演示的示例课程，包含自动评测实验与开放实验各一个。',
                        ?, '2026 秋季学期', '实训', 'PUBLIC', 'PUBLISHED')
                ON DUPLICATE KEY UPDATE id = id
                """, DEMO_COURSE_ID, DEMO_TEACHER_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO crs_course_member
                  (course_id, user_id, role, join_method, join_status, approved_by, joined_at, is_deleted, member_version)
                VALUES (?, ?, 'TEACHER', 'CREATED', 'ACTIVE', NULL, CURRENT_TIMESTAMP, FALSE, 1)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """, DEMO_COURSE_ID, DEMO_TEACHER_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO crs_course_member
                  (course_id, user_id, role, join_method, join_status, approved_by, joined_at, is_deleted, member_version)
                VALUES (?, ?, 'STUDENT', 'PUBLIC', 'ACTIVE', NULL, CURRENT_TIMESTAMP, FALSE, 1)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """, DEMO_COURSE_ID, DEMO_STUDENT_USER_ID);
    }
}
