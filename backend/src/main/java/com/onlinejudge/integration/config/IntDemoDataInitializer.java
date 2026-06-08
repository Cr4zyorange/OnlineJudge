package com.onlinejudge.integration.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Configuration
@ConditionalOnProperty(name = "onlinejudge.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class IntDemoDataInitializer {
    private static final long COURSE_ID = 9501L;
    private static final long CHAPTER_ID = 950101L;
    private static final long RESOURCE_ID = 950102L;
    private static final long LAB_ID = 950201L;
    private static final long LAB_TESTCASE_ID = 950202L;
    private static final long LAB_SUBMISSION_ID = 950203L;
    private static final long LAB_EVALUATION_ID = 950204L;
    private static final long LAB_EVALUATION_RESULT_ID = 950205L;
    private static final long LAB_SCORE_ID = 950206L;
    private static final long HOMEWORK_ID = 950301L;
    private static final long HOMEWORK_QUESTION_ID = 950302L;
    private static final long HOMEWORK_SUBMISSION_ID = 950303L;
    private static final long HOMEWORK_EVALUATION_ID = 950304L;
    private static final long HOMEWORK_REVIEW_LOG_ID = 950305L;
    private static final long GRADE_ITEM_LAB_ID = 950401L;
    private static final long GRADE_ITEM_HOMEWORK_ID = 950402L;
    private static final long GRADE_RECORD_LAB_ID = 950411L;
    private static final long GRADE_RECORD_HOMEWORK_ID = 950412L;
    private static final long GRADE_SUMMARY_ID = 950421L;
    private static final long GRADE_PUBLISH_RECORD_ID = 950431L;
    private static final long GRADE_BATCH_ID = 950441L;
    private static final long TASK_LAB_ID = 950601L;
    private static final long TASK_HOMEWORK_ID = 950602L;
    private static final long PROGRESS_RESOURCE_ID = 950603L;
    private static final long RECORD_RESOURCE_ID = 950604L;

    private static final List<String> REQUIRED_TABLES = List.of(
            "t_auth_user",
            "crs_course",
            "crs_course_member",
            "crs_chapter",
            "crs_resource",
            "crs_announcement",
            "lab_experiment",
            "lab_testcase",
            "lab_submission",
            "lab_evaluation",
            "lab_evaluation_result",
            "lab_score",
            "t_hwk_homework",
            "t_hwk_question",
            "t_hwk_submission",
            "t_hwk_evaluation",
            "t_hwk_review_log",
            "t_grade_item",
            "t_grade_record",
            "t_course_grade_summary",
            "t_grade_publish_record",
            "t_grade_calculation_batch",
            "lrn_learning_task",
            "lrn_learning_progress",
            "lrn_learning_record",
            "lrn_notification",
            "lrn_notification_status_log",
            "lrn_notification_setting"
    );

    @Bean
    @DependsOn("authSeedData")
    @Order(1)
    ApplicationRunner intDemoData(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new DemoDataRunner(jdbcTemplate);
    }

    private static final class DemoDataRunner implements ApplicationRunner {
        private final JdbcTemplate jdbcTemplate;

        private DemoDataRunner(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (!schemaReady()) {
                return;
            }
            Optional<Long> teacherId = userId("teacher001");
            Optional<Long> studentId = userId("student001");
            if (teacherId.isEmpty() || studentId.isEmpty()) {
                return;
            }

            long teacher = teacherId.get();
            long student = studentId.get();
            seedCourse(teacher, student);
            seedLearning(student);
            seedLab(teacher, student);
            seedHomework(teacher, student);
            seedGrades(teacher, student);
            seedNotifications(student);
        }

        private boolean schemaReady() {
            for (String table : REQUIRED_TABLES) {
                if (!tableExists(table)) {
                    return false;
                }
            }
            return true;
        }

        private boolean tableExists(String tableName) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_NAME) = LOWER(?)
                    """, Integer.class, tableName);
            return count != null && count > 0;
        }

        private Optional<Long> userId(String username) {
            List<Long> ids = jdbcTemplate.query(
                    "SELECT user_id FROM t_auth_user WHERE username = ? AND deleted = FALSE",
                    (rs, rowNum) -> rs.getLong("user_id"),
                    username
            );
            return ids.stream().findFirst();
        }

        private void seedCourse(long teacherId, long studentId) {
            insertIfMissing("crs_course", COURSE_ID, """
                    INSERT INTO crs_course (
                        id, course_name, description, teacher_id, semester, category, cover_url,
                        enrollment_mode, invite_code, max_students, start_date, end_date, status,
                        is_deleted, created_at, updated_at
                    ) VALUES (
                        ?, '数据结构全流程演示课', '覆盖登录、课程、学习、实验、作业、成绩、通知的验收演示课程。',
                        ?, '2025-2026-2', '计算机基础', '/assets/back.jpg', 'INVITE',
                        'INT95', 80, DATE '2026-03-01', DATE '2026-07-01', 'ACTIVE',
                        FALSE, TIMESTAMP '2026-06-01 08:00:00', TIMESTAMP '2026-06-01 08:00:00'
                    )
                    """, COURSE_ID, teacherId);
            insertIfMissingByCount("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                    new Object[]{COURSE_ID, teacherId}, """
                            INSERT INTO crs_course_member (
                                course_id, user_id, role, join_method, join_status, approved_by, joined_at,
                                last_access_at, is_deleted, created_at, updated_at
                            ) VALUES (?, ?, 'TEACHER', 'CREATED', 'ACTIVE', ?, TIMESTAMP '2026-06-01 08:00:00',
                                TIMESTAMP '2026-06-08 09:00:00', FALSE, TIMESTAMP '2026-06-01 08:00:00',
                                TIMESTAMP '2026-06-08 09:00:00')
                            """, COURSE_ID, teacherId, teacherId);
            insertIfMissingByCount("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                    new Object[]{COURSE_ID, studentId}, """
                            INSERT INTO crs_course_member (
                                course_id, user_id, role, join_method, join_status, approved_by, joined_at,
                                last_access_at, is_deleted, created_at, updated_at
                            ) VALUES (?, ?, 'STUDENT', 'INVITE_CODE', 'ACTIVE', ?, TIMESTAMP '2026-06-01 08:05:00',
                                TIMESTAMP '2026-06-08 09:10:00', FALSE, TIMESTAMP '2026-06-01 08:05:00',
                                TIMESTAMP '2026-06-08 09:10:00')
                            """, COURSE_ID, studentId, teacherId);
            insertIfMissing("crs_chapter", CHAPTER_ID, """
                    INSERT INTO crs_chapter (
                        id, course_id, parent_id, chapter_name, sort_order, objective,
                        visible_status, chapter_type, is_deleted, created_at, updated_at
                    ) VALUES (?, ?, NULL, '第 1 章 线性表与自动评测', 1,
                        '完成课程资源学习后提交实验和作业，并查看成绩通知。', 1, 1, FALSE,
                        TIMESTAMP '2026-06-01 08:10:00', TIMESTAMP '2026-06-01 08:10:00')
                    """, CHAPTER_ID, COURSE_ID);
            insertIfMissing("crs_resource", RESOURCE_ID, """
                    INSERT INTO crs_resource (
                        id, course_id, chapter_id, resource_name, resource_type, visibility, publish_at,
                        storage_key, original_filename, content_type, file_size, upload_user_id,
                        is_deleted, created_at, updated_at
                    ) VALUES (?, ?, ?, '线性表实验讲义.pdf', 'DOCUMENT', 'STUDENT',
                        TIMESTAMP '2026-06-01 08:15:00', 'demo/int95/linear-list-guide.pdf',
                        '线性表实验讲义.pdf', 'application/pdf', 204800, ?, FALSE,
                        TIMESTAMP '2026-06-01 08:15:00', TIMESTAMP '2026-06-01 08:15:00')
                    """, RESOURCE_ID, COURSE_ID, CHAPTER_ID, teacherId);
            insertIfMissingByCount("SELECT COUNT(*) FROM crs_announcement WHERE course_id = ? AND title = ?",
                    new Object[]{COURSE_ID, "全流程验收演示安排"}, """
                            INSERT INTO crs_announcement (
                                course_id, title, content, is_top, publisher_id, is_deleted, created_at, updated_at
                            ) VALUES (?, '全流程验收演示安排',
                                '请按资源学习、实验提交、作业提交、成绩查看和通知中心的顺序完成演示。',
                                TRUE, ?, FALSE, TIMESTAMP '2026-06-01 08:20:00', TIMESTAMP '2026-06-01 08:20:00')
                            """, COURSE_ID, teacherId);
        }

        private void seedLearning(long studentId) {
            insertIfMissing("lrn_learning_progress", PROGRESS_RESOURCE_ID, """
                    INSERT INTO lrn_learning_progress (
                        id, user_id, course_id, chapter_id, source_module, source_id, progress_percent,
                        last_position, status, updated_at
                    ) VALUES (?, ?, ?, ?, 'CRS', ?, 100, 'page=12', 'COMPLETED',
                        TIMESTAMP '2026-06-08 09:12:00')
                    """, PROGRESS_RESOURCE_ID, studentId, COURSE_ID, CHAPTER_ID, RESOURCE_ID);
            insertIfMissing("lrn_learning_record", RECORD_RESOURCE_ID, """
                    INSERT INTO lrn_learning_record (
                        id, user_id, course_id, source_module, source_id, action_type, duration,
                        started_at, ended_at, created_at
                    ) VALUES (?, ?, ?, 'CRS', ?, 'ACCESS', 1800, TIMESTAMP '2026-06-08 08:40:00',
                        TIMESTAMP '2026-06-08 09:10:00', TIMESTAMP '2026-06-08 09:10:00')
                    """, RECORD_RESOURCE_ID, studentId, COURSE_ID, RESOURCE_ID);
            insertIfMissingByCount("SELECT COUNT(*) FROM lrn_notification_setting WHERE user_id = ?",
                    new Object[]{studentId}, """
                            INSERT INTO lrn_notification_setting (
                                user_id, enable_experiment, enable_homework, enable_grade, enable_announcement,
                                enable_non_critical_reminder, created_at, updated_at
                            ) VALUES (?, TRUE, TRUE, TRUE, TRUE, TRUE,
                                TIMESTAMP '2026-06-01 08:30:00', TIMESTAMP '2026-06-01 08:30:00')
                            """, studentId);
        }

        private void seedLab(long teacherId, long studentId) {
            insertIfMissing("lab_experiment", LAB_ID, """
                    INSERT INTO lab_experiment (
                        id, course_id, chapter_id, title, description, status, deadline, max_score,
                        attachment_ids, allowed_languages, evaluation_mode, auto_evaluate, report_required,
                        time_limit_ms, memory_limit_kb, created_by, published_at, deleted, created_at, updated_at
                    ) VALUES (?, ?, ?, '实验一：线性表操作', '提交顺序表插入与删除程序，系统自动评测并发布成绩。',
                        'SCORE_PUBLISHED', TIMESTAMP '2026-06-30 23:59:59', 100, NULL, 'python',
                        'DOCKER_IO', TRUE, FALSE, 60000, 262144, ?, TIMESTAMP '2026-06-01 09:00:00',
                        FALSE, TIMESTAMP '2026-06-01 08:50:00', TIMESTAMP '2026-06-08 09:20:00')
                    """, LAB_ID, COURSE_ID, CHAPTER_ID, teacherId);
            insertIfMissing("lab_testcase", LAB_TESTCASE_ID, """
                    INSERT INTO lab_testcase (
                        id, lab_id, input, expected_output, score_weight, is_public, time_limit_ms,
                        memory_limit_kb, order_num, deleted, created_at, updated_at
                    ) VALUES (?, ?, '3\\n1 2 3\\n', '1 2 3\\n', 100, TRUE, 60000, 262144, 1,
                        FALSE, TIMESTAMP '2026-06-01 09:05:00', TIMESTAMP '2026-06-01 09:05:00')
                    """, LAB_TESTCASE_ID, LAB_ID);
            insertIfMissing("lab_submission", LAB_SUBMISSION_ID, """
                    INSERT INTO lab_submission (
                        id, lab_id, student_id, code_content, file_id, language, submit_status,
                        evaluation_status, final_score, auto_score, version, is_final,
                        submitted_at, created_at, updated_at, deleted
                    ) VALUES (?, ?, ?, 'print("1 2 3")', NULL, 'python', 'SUBMITTED',
                        'ACCEPTED', 92, 92, 1, TRUE, TIMESTAMP '2026-06-08 09:16:00',
                        TIMESTAMP '2026-06-08 09:16:00', TIMESTAMP '2026-06-08 09:18:00', FALSE)
                    """, LAB_SUBMISSION_ID, LAB_ID, studentId);
            insertIfMissing("lab_evaluation", LAB_EVALUATION_ID, """
                    INSERT INTO lab_evaluation (
                        id, submission_id, status, score, passed_cases, total_cases, time_used_ms,
                        memory_used_kb, feedback, compile_log, run_log, started_at, finished_at,
                        created_at, updated_at
                    ) VALUES (?, ?, 'ACCEPTED', 92, 1, 1, 120, 2048, '全部用例通过',
                        '', 'case#1 accepted', TIMESTAMP '2026-06-08 09:16:10',
                        TIMESTAMP '2026-06-08 09:16:20', TIMESTAMP '2026-06-08 09:16:10',
                        TIMESTAMP '2026-06-08 09:16:20')
                    """, LAB_EVALUATION_ID, LAB_SUBMISSION_ID);
            insertIfMissing("lab_evaluation_result", LAB_EVALUATION_RESULT_ID, """
                    INSERT INTO lab_evaluation_result (
                        id, submission_id, testcase_id, status, passed, score, actual_output,
                        message, executed_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 'ACCEPTED', TRUE, 92, '1 2 3\\n', '输出匹配',
                        TIMESTAMP '2026-06-08 09:16:18', TIMESTAMP '2026-06-08 09:16:18',
                        TIMESTAMP '2026-06-08 09:16:18')
                    """, LAB_EVALUATION_RESULT_ID, LAB_SUBMISSION_ID, LAB_TESTCASE_ID);
            insertIfMissing("lab_score", LAB_SCORE_ID, """
                    INSERT INTO lab_score (
                        id, submission_id, report_id, teacher_id, auto_score, report_score, manual_score,
                        final_score, comment, scored_at, updated_at
                    ) VALUES (?, ?, NULL, ?, 92, NULL, NULL, 92, '自动评测通过，成绩发布。',
                        TIMESTAMP '2026-06-08 09:20:00', TIMESTAMP '2026-06-08 09:20:00')
                    """, LAB_SCORE_ID, LAB_SUBMISSION_ID, teacherId);
            insertIfMissing("lrn_learning_task", TASK_LAB_ID, """
                    INSERT INTO lrn_learning_task (
                        id, user_id, course_id, source_module, source_id, task_type, title, deadline,
                        progress, status, action_url, snapshot_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 'LAB', ?, 'EXPERIMENT', '实验一：线性表操作',
                        TIMESTAMP '2026-06-30 23:59:59', 100, 'COMPLETED',
                        '/courses/9501/labs/950201?role=student',
                        TIMESTAMP '2026-06-08 09:20:00', TIMESTAMP '2026-06-01 09:00:00',
                        TIMESTAMP '2026-06-08 09:20:00')
                    """, TASK_LAB_ID, studentId, COURSE_ID, LAB_ID);
        }

        private void seedHomework(long teacherId, long studentId) {
            insertIfMissing("t_hwk_homework", HOMEWORK_ID, """
                    INSERT INTO t_hwk_homework (
                        id, course_id, chapter_id, title, description, type, status, total_score,
                        deadline, allow_resubmit, allow_late_submit, show_evaluation_before_publish,
                        judge_config_id, created_by, published_at, is_deleted, created_at, updated_at
                    ) VALUES (?, ?, ?, '作业一：线性表复杂度分析', '完成一次文本作业提交并由教师批阅发布成绩。',
                        'TEXT', 'SCORE_PUBLISHED', 100.00, TIMESTAMP '2026-06-30 23:59:59',
                        TRUE, FALSE, TRUE, NULL, ?, TIMESTAMP '2026-06-01 09:30:00',
                        FALSE, TIMESTAMP '2026-06-01 09:25:00', TIMESTAMP '2026-06-08 09:30:00')
                    """, HOMEWORK_ID, COURSE_ID, CHAPTER_ID, teacherId);
            insertIfMissing("t_hwk_question", HOMEWORK_QUESTION_ID, """
                    INSERT INTO t_hwk_question (
                        id, homework_id, question_type, stem, options_json, answer_json, score,
                        sort_order, created_at, updated_at
                    ) VALUES (?, ?, 'TEXT', '说明顺序表插入操作的时间复杂度。', NULL,
                        '{"reference":"平均 O(n)，尾插 O(1)。"}', 100.00, 1,
                        TIMESTAMP '2026-06-01 09:35:00', TIMESTAMP '2026-06-01 09:35:00')
                    """, HOMEWORK_QUESTION_ID, HOMEWORK_ID);
            insertIfMissing("t_hwk_submission", HOMEWORK_SUBMISSION_ID, """
                    INSERT INTO t_hwk_submission (
                        id, homework_id, student_id, submit_type, answer_text, answer_json, file_url,
                        language, submit_status, evaluation_status, review_status, auto_score,
                        manual_score, final_score, comment, version, is_final, submitted_at,
                        reviewed_by, reviewed_at, created_at, updated_at, is_deleted
                    ) VALUES (?, ?, ?, 'TEXT', '顺序表插入平均需要移动 n/2 个元素，复杂度为 O(n)。',
                        NULL, NULL, NULL, 'SUBMITTED', 'NONE', 'REVIEWED', NULL, 88.00,
                        88.00, '分析完整，边界情况可继续补充。', 1, TRUE,
                        TIMESTAMP '2026-06-08 09:24:00', ?, TIMESTAMP '2026-06-08 09:28:00',
                        TIMESTAMP '2026-06-08 09:24:00', TIMESTAMP '2026-06-08 09:28:00', FALSE)
                    """, HOMEWORK_SUBMISSION_ID, HOMEWORK_ID, studentId, teacherId);
            insertIfMissing("t_hwk_evaluation", HOMEWORK_EVALUATION_ID, """
                    INSERT INTO t_hwk_evaluation (
                        id, submission_id, homework_id, student_id, evaluation_type, status, score,
                        passed_cases, total_cases, time_used_ms, memory_used_kb, error_message,
                        feedback, log_url, compile_log, run_log, reevaluation, triggered_by,
                        started_at, finished_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'OBJECTIVE_AUTO', 'ACCEPTED', 88.00, 1, 1, NULL, NULL, NULL,
                        '教师批阅完成', NULL, NULL, NULL, FALSE, ?, TIMESTAMP '2026-06-08 09:28:00',
                        TIMESTAMP '2026-06-08 09:28:00', TIMESTAMP '2026-06-08 09:28:00',
                        TIMESTAMP '2026-06-08 09:28:00')
                    """, HOMEWORK_EVALUATION_ID, HOMEWORK_SUBMISSION_ID, HOMEWORK_ID, studentId, teacherId);
            insertIfMissing("t_hwk_review_log", HOMEWORK_REVIEW_LOG_ID, """
                    INSERT INTO t_hwk_review_log (
                        id, submission_id, homework_id, student_id, operation_type, old_score,
                        new_score, comment, operator_id, reason, created_at
                    ) VALUES (?, ?, ?, ?, 'REVIEW', NULL, 88.00,
                        '验收演示教师批阅记录', ?, 'INT-06 演示数据',
                        TIMESTAMP '2026-06-08 09:28:00')
                    """, HOMEWORK_REVIEW_LOG_ID, HOMEWORK_SUBMISSION_ID, HOMEWORK_ID, studentId, teacherId);
            insertIfMissing("lrn_learning_task", TASK_HOMEWORK_ID, """
                    INSERT INTO lrn_learning_task (
                        id, user_id, course_id, source_module, source_id, task_type, title, deadline,
                        progress, status, action_url, snapshot_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 'HWK', ?, 'HOMEWORK', '作业一：线性表复杂度分析',
                        TIMESTAMP '2026-06-30 23:59:59', 100, 'COMPLETED',
                        '/courses/9501/homeworks/950301?role=student',
                        TIMESTAMP '2026-06-08 09:30:00', TIMESTAMP '2026-06-01 09:30:00',
                        TIMESTAMP '2026-06-08 09:30:00')
                    """, TASK_HOMEWORK_ID, studentId, COURSE_ID, HOMEWORK_ID);
        }

        private void seedGrades(long teacherId, long studentId) {
            insertIfMissing("t_grade_item", GRADE_ITEM_LAB_ID, """
                    INSERT INTO t_grade_item (
                        id, course_id, name, source_type, source_id, full_score, weight,
                        included_in_final, enabled, sort_order, created_by, deleted, created_at, updated_at
                    ) VALUES (?, ?, '实验一成绩', 'LAB', ?, 100.00, 0.4000, TRUE, TRUE, 1, ?,
                        FALSE, TIMESTAMP '2026-06-08 09:32:00', TIMESTAMP '2026-06-08 09:32:00')
                    """, GRADE_ITEM_LAB_ID, COURSE_ID, LAB_ID, teacherId);
            insertIfMissing("t_grade_item", GRADE_ITEM_HOMEWORK_ID, """
                    INSERT INTO t_grade_item (
                        id, course_id, name, source_type, source_id, full_score, weight,
                        included_in_final, enabled, sort_order, created_by, deleted, created_at, updated_at
                    ) VALUES (?, ?, '作业一成绩', 'HWK', ?, 100.00, 0.6000, TRUE, TRUE, 2, ?,
                        FALSE, TIMESTAMP '2026-06-08 09:32:00', TIMESTAMP '2026-06-08 09:32:00')
                    """, GRADE_ITEM_HOMEWORK_ID, COURSE_ID, HOMEWORK_ID, teacherId);
            insertIfMissing("t_grade_calculation_batch", GRADE_BATCH_ID, """
                    INSERT INTO t_grade_calculation_batch (
                        id, course_id, trigger_type, affected_item_count, affected_student_count,
                        status, message, calculated_by, calculated_at
                    ) VALUES (?, ?, 'MANUAL_SYNC', 2, 1, 'SUCCESS', 'INT-06 演示成绩同步完成',
                        ?, TIMESTAMP '2026-06-08 09:34:00')
                    """, GRADE_BATCH_ID, COURSE_ID, teacherId);
            insertIfMissing("t_grade_record", GRADE_RECORD_LAB_ID, """
                    INSERT INTO t_grade_record (
                        id, course_id, student_id, grade_item_id, source_type, source_id, raw_score,
                        weighted_score, grade_status, publish_status, comment, source_updated_at,
                        calculated_at, published_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'LAB', ?, 92.00, 36.80, 'SCORED', 'PUBLISHED',
                        '实验成绩来自自动评测', TIMESTAMP '2026-06-08 09:20:00',
                        TIMESTAMP '2026-06-08 09:34:00', TIMESTAMP '2026-06-08 09:35:00',
                        TIMESTAMP '2026-06-08 09:34:00', TIMESTAMP '2026-06-08 09:35:00')
                    """, GRADE_RECORD_LAB_ID, COURSE_ID, studentId, GRADE_ITEM_LAB_ID, LAB_ID);
            insertIfMissing("t_grade_record", GRADE_RECORD_HOMEWORK_ID, """
                    INSERT INTO t_grade_record (
                        id, course_id, student_id, grade_item_id, source_type, source_id, raw_score,
                        weighted_score, grade_status, publish_status, comment, source_updated_at,
                        calculated_at, published_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'HWK', ?, 88.00, 52.80, 'SCORED', 'PUBLISHED',
                        '作业成绩来自教师批阅', TIMESTAMP '2026-06-08 09:28:00',
                        TIMESTAMP '2026-06-08 09:34:00', TIMESTAMP '2026-06-08 09:35:00',
                        TIMESTAMP '2026-06-08 09:34:00', TIMESTAMP '2026-06-08 09:35:00')
                    """, GRADE_RECORD_HOMEWORK_ID, COURSE_ID, studentId, GRADE_ITEM_HOMEWORK_ID, HOMEWORK_ID);
            insertIfMissing("t_course_grade_summary", GRADE_SUMMARY_ID, """
                    INSERT INTO t_course_grade_summary (
                        id, course_id, student_id, final_score, final_status, publish_status,
                        calculation_batch_id, published_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 89.60, 'CALCULATED', 'PUBLISHED', ?,
                        TIMESTAMP '2026-06-08 09:35:00', TIMESTAMP '2026-06-08 09:34:00',
                        TIMESTAMP '2026-06-08 09:35:00')
                    """, GRADE_SUMMARY_ID, COURSE_ID, studentId, GRADE_BATCH_ID);
            insertIfMissing("t_grade_publish_record", GRADE_PUBLISH_RECORD_ID, """
                    INSERT INTO t_grade_publish_record (
                        id, course_id, idempotency_key, publish_scope, published_count, published_by,
                        published_at, notification_status, remark
                    ) VALUES (?, ?, 'int95-demo-grade-publish', 'PARTIAL_STUDENTS', 1, ?,
                        TIMESTAMP '2026-06-08 09:35:00', 'SENT', 'INT-06 演示成绩发布')
                    """, GRADE_PUBLISH_RECORD_ID, COURSE_ID, teacherId);
        }

        private void seedNotifications(long studentId) {
            seedNotification(950501L, studentId, "int95-lab-published", "TASK", "实验已发布",
                    "实验一：线性表操作已发布，请按时提交。", "LAB", LAB_ID,
                    "/courses/9501/labs/950201?role=student", 2);
            seedNotification(950502L, studentId, "int95-lab-score", "GRADE", "实验成绩已发布",
                    "实验一成绩已发布，可查看自动评测结果和教师反馈。", "LAB", LAB_ID,
                    "/courses/9501/labs/950201/submissions?role=student", 3);
            seedNotification(950503L, studentId, "int95-homework-published", "TASK", "作业已发布",
                    "作业一：线性表复杂度分析已发布。", "HWK", HOMEWORK_ID,
                    "/courses/9501/homeworks/950301?role=student", 2);
            seedNotification(950504L, studentId, "int95-course-grade", "GRADE", "课程成绩已发布",
                    "数据结构全流程演示课成绩已发布，请查看成绩明细。", "GRD", GRADE_SUMMARY_ID,
                    "/courses/9501?page=grades&role=student", 3);
        }

        private void seedNotification(
                long id,
                long userId,
                String idempotencyKey,
                String type,
                String title,
                String content,
                String sourceModule,
                long sourceId,
                String actionUrl,
                int priority
        ) {
            insertIfMissingByCount("""
                    SELECT COUNT(*)
                    FROM lrn_notification
                    WHERE user_id = ?
                      AND idempotency_key = ?
                    """, new Object[]{userId, idempotencyKey}, """
                    INSERT INTO lrn_notification (
                        id, user_id, course_id, idempotency_key, title, content, type, priority,
                        is_read, source_module, source_id, action_url, created_at, read_at, deleted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?, ?, TIMESTAMP '2026-06-08 09:36:00',
                        NULL, NULL)
                    """, id, userId, COURSE_ID, idempotencyKey, title, content, type, priority,
                    sourceModule, sourceId, actionUrl);
            insertIfMissingByCount("""
                    SELECT COUNT(*)
                    FROM lrn_notification_status_log
                    WHERE notification_id = ?
                      AND operation_type = 'CREATE'
                    """, new Object[]{id}, """
                    INSERT INTO lrn_notification_status_log (
                        notification_id, user_id, old_status, new_status, operation_type, operated_at
                    ) VALUES (?, ?, NULL, 'UNREAD', 'CREATE', TIMESTAMP '2026-06-08 09:36:00')
                    """, id, userId);
        }

        private void insertIfMissing(String tableName, long id, String sql, Object... args) {
            insertIfMissingByCount("SELECT COUNT(*) FROM " + tableName + " WHERE id = ?", new Object[]{id}, sql, args);
        }

        private void insertIfMissingByCount(String countSql, Object[] countArgs, String insertSql, Object... insertArgs) {
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, countArgs);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.update(insertSql, insertArgs);
        }
    }
}
