package com.onlinejudge.lrn.controller;

import com.onlinejudge.lrn.service.ReminderRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_reminder_failure_logging;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "onlinejudge.lrn.reminders.scheduling-enabled=false",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260525_02_create_lab_experiment.sql,file:../database/migrations/20260526_01_create_lab_submission.sql,file:../database/migrations/20260530_01_create_hwk_homework.sql,file:../database/migrations/20260601_01_create_hwk_submission.sql,file:../database/migrations/20260603_01_create_lrn_notification.sql,file:../database/migrations/20260605_01_create_lrn_reminder_rule.sql"
})
class ReminderRuleFailureLoggingTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReminderRuleService reminderRuleService;

    @Test
    void failedReminderDeliveryStillPersistsFailedScanLog() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 9, 0);
        insertCourse(201L, "Reliable Reminder Course");
        insertStudent(601L);
        insertMember(201L, 601L);
        insertHomework(701L, 201L, "Reliability homework", now.plusMinutes(55));

        jdbcTemplate.execute("DROP TABLE lrn_notification_status_log");

        assertThatThrownBy(() -> reminderRuleService.scanDeadlineReminders(now))
                .isInstanceOf(RuntimeException.class);

        Long failedLogCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_reminder_scan_log
                WHERE retry_status = 'FAILED'
                  AND triggered_count = 0
                  AND failed_reason IS NOT NULL
                """, Long.class);
        Long notificationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lrn_notification", Long.class);

        assertThat(failedLogCount).isEqualTo(1);
        assertThat(notificationCount).isZero();
    }

    private void insertCourse(long courseId, String courseName) {
        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, courseId, courseName, "course description", 501L, "PUBLISHED");
    }

    private void insertStudent(long userId) {
        jdbcTemplate.update("""
                INSERT INTO t_auth_user
                    (user_id, username, password_hash, user_type, display_name, phone, email, account_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, "nfr-student", "hash", "STUDENT", "NFR Student", "13900006501",
                "nfr-student@example.com", "ACTIVE");
    }

    private void insertMember(long courseId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, courseId, userId, "STUDENT", "ACTIVE");
    }

    private void insertHomework(long homeworkId, long courseId, String title, LocalDateTime deadline) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                    (id, course_id, title, description, type, status, total_score, deadline, created_by, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, homeworkId, courseId, title, "homework description", "PROGRAMMING", "PUBLISHED",
                100, deadline, 501L, deadline.minusDays(7));
    }
}
