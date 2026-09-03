package com.onlinejudge.assessmentservice.config;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Issue #396: mirrors the monolith demo LAB set (950201 graded, 950211 open) onto a clean
 * Assessment database when demo data is enabled.
 *
 * <p>The demo course id "9501" is provisioned by the Course service's CourseDemoDataInitializer;
 * the ACTIVE member projection rows let CourseMembershipGuard accept student001/teacher001 for the
 * demo course without waiting for roster events. The table-level guard plus ON DUPLICATE KEY
 * UPDATE keeps restarts and concurrent boots idempotent.
 */
@Component
@ConditionalOnProperty(name = "onlinejudge.demo-data.enabled", havingValue = "true", matchIfMissing = false)
public class AssessmentLabDemoDataInitializer implements ApplicationRunner {

    static final long DEMO_GRADED_LAB_ID = 950201L;
    static final long DEMO_OPEN_LAB_ID = 950211L;
    static final String DEMO_COURSE_ID = "9501";
    static final String DEMO_SUBMISSION_ID = "95020300-0000-0000-0000-000000095201";

    private final JdbcTemplate jdbcTemplate;

    public AssessmentLabDemoDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    public void seedIfEmpty() {
        Integer existingLabs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assessment_lab_experiment", Integer.class);
        if (existingLabs == null || existingLabs > 0) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp deadline = Timestamp.valueOf(LocalDateTime.now().plusDays(30));
        jdbcTemplate.update("""
                INSERT INTO assessment_lab_experiment
                  (id, course_id, title, description, status, deadline, max_score,
                   allowed_languages, auto_evaluate, created_by, created_at, updated_at)
                VALUES (?, ?, '实验一：线性表操作', '提交顺序表插入与删除程序，系统自动评测并发布成绩。',
                        'SCORE_PUBLISHED', ?, 100, 'python', TRUE, '2', ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, DEMO_GRADED_LAB_ID, DEMO_COURSE_ID, deadline, now, now);
        jdbcTemplate.update("""
                INSERT INTO assessment_lab_experiment
                  (id, course_id, title, description, status, deadline, max_score,
                   allowed_languages, auto_evaluate, created_by, created_at, updated_at)
                VALUES (?, ?, '开放实验：自选题实训', '自选题开放实训，教师在批阅工作台手动评分。',
                        'PUBLISHED', ?, 100, 'python', FALSE, '2', ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, DEMO_OPEN_LAB_ID, DEMO_COURSE_ID, deadline, now, now);
        jdbcTemplate.update("""
                INSERT INTO assessment_lab_testcase
                  (lab_id, input_text, expected_output, score_weight, is_public, order_num)
                VALUES (?, '3\n1 2 3\n', '1 2 3\n', 100, TRUE, 1)
                ON DUPLICATE KEY UPDATE lab_id = lab_id
                """, DEMO_GRADED_LAB_ID);
        jdbcTemplate.update("""
                INSERT INTO assessment_lab_submission
                  (submission_id, lab_id, student_id, submission_version, language, submit_status,
                   has_file, auto_score, final_score, submitted_at)
                VALUES (?, ?, '1', 1, 'python', 'SUBMITTED', FALSE, 92, 92, ?)
                ON DUPLICATE KEY UPDATE submission_id = submission_id
                """, DEMO_SUBMISSION_ID, DEMO_GRADED_LAB_ID, now);
        Long testcaseId = jdbcTemplate.queryForObject(
                "SELECT id FROM assessment_lab_testcase WHERE lab_id = ? AND order_num = 1",
                Long.class, DEMO_GRADED_LAB_ID);
        jdbcTemplate.update("""
                INSERT INTO assessment_lab_evaluation_result
                  (submission_id, testcase_id, passed, score, actual_output, message, executed_at)
                VALUES (?, ?, TRUE, 92, '1 2 3\n', '输出匹配', ?)
                ON DUPLICATE KEY UPDATE submission_id = submission_id
                """, DEMO_SUBMISSION_ID, testcaseId, now);
        jdbcTemplate.update("""
                INSERT INTO assessment_lab_score
                  (submission_id, lab_id, auto_score, report_score, manual_score, final_score, comment, scored_at, updated_at)
                VALUES (?, ?, 92, NULL, NULL, 92, '自动评测通过，成绩发布。', ?, ?)
                ON DUPLICATE KEY UPDATE submission_id = submission_id
                """, DEMO_SUBMISSION_ID, DEMO_GRADED_LAB_ID, now, now);
        jdbcTemplate.update("""
                INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version)
                VALUES (?, '1', 'ACTIVE', 1), (?, '2', 'ACTIVE', 1)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """, DEMO_COURSE_ID, DEMO_COURSE_ID);
    }
}
