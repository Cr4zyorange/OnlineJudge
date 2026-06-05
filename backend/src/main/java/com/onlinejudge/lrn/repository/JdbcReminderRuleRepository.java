package com.onlinejudge.lrn.repository;

import com.onlinejudge.lrn.service.NotificationSettingItem;
import com.onlinejudge.lrn.service.ReminderRuleItem;
import com.onlinejudge.lrn.service.ReminderTaskTarget;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReminderRuleRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcReminderRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReminderRuleItem> findRules(long userId) {
        return jdbcTemplate.query("""
                SELECT reminder_type, source_module, ahead_minutes, enabled, required
                FROM lrn_reminder_rule
                WHERE user_id = ?
                ORDER BY source_module DESC, reminder_type, ahead_minutes DESC
                """, this::mapRule, userId);
    }

    public Optional<NotificationSettingItem> findSetting(long userId) {
        List<NotificationSettingItem> settings = jdbcTemplate.query("""
                SELECT enable_experiment, enable_homework, enable_grade, enable_announcement, enable_non_critical_reminder
                FROM lrn_notification_setting
                WHERE user_id = ?
                """, (rs, rowNum) -> new NotificationSettingItem(
                rs.getBoolean("enable_experiment"),
                rs.getBoolean("enable_homework"),
                rs.getBoolean("enable_grade"),
                rs.getBoolean("enable_announcement"),
                rs.getBoolean("enable_non_critical_reminder")
        ), userId);
        return settings.stream().findFirst();
    }

    public void upsertRule(long userId, ReminderRuleItem rule) {
        int updated = jdbcTemplate.update("""
                UPDATE lrn_reminder_rule
                SET enabled = ?,
                    required = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND reminder_type = ?
                  AND source_module = ?
                  AND ahead_minutes = ?
                """, rule.enabled(), rule.required(), userId, rule.reminderType(), rule.sourceModule(), rule.aheadMinutes());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO lrn_reminder_rule
                        (user_id, reminder_type, source_module, ahead_minutes, enabled, required)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, rule.reminderType(), rule.sourceModule(), rule.aheadMinutes(), rule.enabled(), rule.required());
        }
    }

    public void upsertSetting(long userId, NotificationSettingItem setting) {
        int updated = jdbcTemplate.update("""
                UPDATE lrn_notification_setting
                SET enable_experiment = ?,
                    enable_homework = ?,
                    enable_grade = ?,
                    enable_announcement = ?,
                    enable_non_critical_reminder = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, setting.enableExperiment(), setting.enableHomework(), setting.enableGrade(),
                setting.enableAnnouncement(), setting.enableNonCriticalReminder(), userId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO lrn_notification_setting
                        (user_id, enable_experiment, enable_homework, enable_grade, enable_announcement, enable_non_critical_reminder)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, setting.enableExperiment(), setting.enableHomework(), setting.enableGrade(),
                    setting.enableAnnouncement(), setting.enableNonCriticalReminder());
        }
    }

    public List<Long> findUsersWithUpcomingDeadlineTasks(LocalDateTime now, LocalDateTime latestDeadline) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT member.user_id
                FROM crs_course_member member
                INNER JOIN crs_course course ON course.id = member.course_id
                WHERE member.role = 'STUDENT'
                  AND member.join_status = 'ACTIVE'
                  AND member.is_deleted = FALSE
                  AND course.is_deleted = FALSE
                  AND (
                    EXISTS (
                        SELECT 1
                        FROM t_hwk_homework homework
                        WHERE homework.course_id = member.course_id
                          AND homework.status = 'PUBLISHED'
                          AND homework.is_deleted = FALSE
                          AND homework.deadline > ?
                          AND homework.deadline <= ?
                          AND NOT EXISTS (
                              SELECT 1
                              FROM t_hwk_submission submission
                              WHERE submission.homework_id = homework.id
                                AND submission.student_id = member.user_id
                                AND submission.is_deleted = FALSE
                          )
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM lab_experiment lab
                        WHERE lab.course_id = member.course_id
                          AND lab.status = 'PUBLISHED'
                          AND lab.deleted = FALSE
                          AND lab.deadline > ?
                          AND lab.deadline <= ?
                          AND NOT EXISTS (
                              SELECT 1
                              FROM lab_submission submission
                              WHERE submission.lab_id = lab.id
                                AND submission.student_id = member.user_id
                                AND submission.deleted = FALSE
                          )
                    )
                  )
                """, Long.class, now, latestDeadline, now, latestDeadline);
    }

    public List<ReminderTaskTarget> findHomeworkTargets(long userId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        return jdbcTemplate.query("""
                SELECT member.user_id, homework.course_id, homework.id AS source_id, homework.title, homework.deadline
                FROM t_hwk_homework homework
                INNER JOIN crs_course_member member ON member.course_id = homework.course_id
                INNER JOIN crs_course course ON course.id = homework.course_id
                WHERE member.user_id = ?
                  AND member.role = 'STUDENT'
                  AND member.join_status = 'ACTIVE'
                  AND member.is_deleted = FALSE
                  AND course.is_deleted = FALSE
                  AND homework.status = 'PUBLISHED'
                  AND homework.is_deleted = FALSE
                  AND homework.deadline > ?
                  AND homework.deadline <= ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM t_hwk_submission submission
                      WHERE submission.homework_id = homework.id
                        AND submission.student_id = member.user_id
                        AND submission.is_deleted = FALSE
                  )
                ORDER BY homework.deadline ASC, homework.id ASC
                """, (rs, rowNum) -> mapTarget(rs, "HWK", "/courses/%d/homeworks/%d?role=student"),
                userId, windowStart, windowEnd);
    }

    public List<ReminderTaskTarget> findLabTargets(long userId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        return jdbcTemplate.query("""
                SELECT member.user_id, lab.course_id, lab.id AS source_id, lab.title, lab.deadline
                FROM lab_experiment lab
                INNER JOIN crs_course_member member ON member.course_id = lab.course_id
                INNER JOIN crs_course course ON course.id = lab.course_id
                WHERE member.user_id = ?
                  AND member.role = 'STUDENT'
                  AND member.join_status = 'ACTIVE'
                  AND member.is_deleted = FALSE
                  AND course.is_deleted = FALSE
                  AND lab.status = 'PUBLISHED'
                  AND lab.deleted = FALSE
                  AND lab.deadline > ?
                  AND lab.deadline <= ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM lab_submission submission
                      WHERE submission.lab_id = lab.id
                        AND submission.student_id = member.user_id
                        AND submission.deleted = FALSE
                  )
                ORDER BY lab.deadline ASC, lab.id ASC
                """, (rs, rowNum) -> mapTarget(rs, "LAB", "/courses/%d/labs/%d?role=student"),
                userId, windowStart, windowEnd);
    }

    public void insertScanLog(String batchId, LocalDateTime startedAt, LocalDateTime endedAt,
                              int triggeredCount, String failedReason, String retryStatus) {
        jdbcTemplate.update("""
                INSERT INTO lrn_reminder_scan_log
                    (batch_id, scan_started_at, scan_ended_at, triggered_count, failed_reason, retry_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, batchId, startedAt, endedAt, triggeredCount, failedReason, retryStatus);
    }

    private ReminderRuleItem mapRule(ResultSet rs, int rowNum) throws SQLException {
        return new ReminderRuleItem(
                rs.getString("reminder_type"),
                rs.getString("source_module"),
                rs.getInt("ahead_minutes"),
                rs.getBoolean("enabled"),
                rs.getBoolean("required")
        );
    }

    private ReminderTaskTarget mapTarget(ResultSet rs, String sourceModule, String actionTemplate) throws SQLException {
        long courseId = rs.getLong("course_id");
        long sourceId = rs.getLong("source_id");
        return new ReminderTaskTarget(
                rs.getLong("user_id"),
                courseId,
                sourceId,
                sourceModule,
                rs.getString("title"),
                rs.getObject("deadline", LocalDateTime.class),
                actionTemplate.formatted(courseId, sourceId)
        );
    }
}
