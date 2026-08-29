package com.onlinejudge.lrn.repository;

import com.onlinejudge.integration.learning.LearningAssessmentClient;
import com.onlinejudge.integration.learning.LearningCourseClient;
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
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class JdbcReminderRuleRepository {
    private final JdbcTemplate jdbcTemplate;
    private final LearningCourseClient courseClient;
    private final LearningAssessmentClient assessmentClient;

    public JdbcReminderRuleRepository(JdbcTemplate jdbcTemplate, LearningCourseClient courseClient,
                                      LearningAssessmentClient assessmentClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.courseClient = courseClient;
        this.assessmentClient = assessmentClient;
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
        Map<Long, List<Long>> coursesByUser = courseClient.findAllActiveStudents().stream().collect(Collectors.groupingBy(
                LearningCourseClient.StudentMembership::userId,
                Collectors.mapping(LearningCourseClient.StudentMembership::courseId, Collectors.toList())));
        return coursesByUser.entrySet().stream().filter(entry ->
                !assessmentClient.findUpcomingTasks(entry.getKey(), entry.getValue(), now, latestDeadline, "HWK").isEmpty()
                        || !assessmentClient.findUpcomingTasks(entry.getKey(), entry.getValue(), now, latestDeadline, "LAB").isEmpty())
                .map(Map.Entry::getKey).sorted().toList();
    }

    public List<ReminderTaskTarget> findHomeworkTargets(long userId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        return assessmentClient.findUpcomingTasks(userId, courseClient.findActiveCourseIds(userId), windowStart, windowEnd, "HWK")
                .stream().map(task -> target(userId, task)).toList();
    }

    public List<ReminderTaskTarget> findLabTargets(long userId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        return assessmentClient.findUpcomingTasks(userId, courseClient.findActiveCourseIds(userId), windowStart, windowEnd, "LAB")
                .stream().map(task -> target(userId, task)).toList();
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

    private ReminderTaskTarget target(long userId, LearningAssessmentClient.DeadlineTask task) {
        String segment = "HWK".equals(task.sourceModule()) ? "homeworks" : "labs";
        return new ReminderTaskTarget(userId, task.courseId(), task.sourceId(), task.sourceModule(), task.title(),
                task.deadline(), "/courses/%d/%s/%d?role=student".formatted(task.courseId(), segment, task.sourceId()));
    }
}
