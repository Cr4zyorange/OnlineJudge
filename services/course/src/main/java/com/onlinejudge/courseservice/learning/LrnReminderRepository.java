package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Course-owned reminder rule/setting facts (LRN folded into Course, #355). */
@Repository
public class LrnReminderRepository {
    private final JdbcTemplate jdbc;

    public LrnReminderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<RuleRow> listRules(long userId) {
        return jdbc.query("""
                SELECT reminder_type, source_module, ahead_minutes, enabled, required
                  FROM lrn_reminder_rule WHERE user_id = ?
                 ORDER BY reminder_type, source_module, ahead_minutes
                """, (rs, row) -> new RuleRow(rs.getString("reminder_type"), rs.getString("source_module"),
                rs.getInt("ahead_minutes"), rs.getBoolean("enabled"), rs.getBoolean("required")), userId);
    }

    public void replaceRules(long userId, List<RuleRow> rules) {
        jdbc.update("DELETE FROM lrn_reminder_rule WHERE user_id = ?", userId);
        for (RuleRow rule : rules) {
            jdbc.update("""
                    INSERT INTO lrn_reminder_rule (user_id, reminder_type, source_module, ahead_minutes, enabled, required)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, rule.reminderType(), rule.sourceModule(), rule.aheadMinutes(), rule.enabled(), rule.required());
        }
    }

    public Optional<SettingRow> getSetting(long userId) {
        return jdbc.query("""
                SELECT enable_experiment, enable_homework, enable_grade, enable_announcement, enable_non_critical_reminder
                  FROM lrn_notification_setting WHERE user_id = ?
                """, rs -> rs.next() ? Optional.of(new SettingRow(rs.getBoolean("enable_experiment"),
                rs.getBoolean("enable_homework"), rs.getBoolean("enable_grade"), rs.getBoolean("enable_announcement"),
                rs.getBoolean("enable_non_critical_reminder"))) : Optional.empty(), userId);
    }

    public void saveSetting(long userId, SettingRow setting) {
        int updated = jdbc.update("""
                UPDATE lrn_notification_setting
                   SET enable_experiment = ?, enable_homework = ?, enable_grade = ?, enable_announcement = ?,
                       enable_non_critical_reminder = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """, setting.enableExperiment(), setting.enableHomework(), setting.enableGrade(), setting.enableAnnouncement(),
                setting.enableNonCriticalReminder(), userId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO lrn_notification_setting
                        (user_id, enable_experiment, enable_homework, enable_grade, enable_announcement, enable_non_critical_reminder)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, setting.enableExperiment(), setting.enableHomework(), setting.enableGrade(),
                    setting.enableAnnouncement(), setting.enableNonCriticalReminder());
        }
    }

    public record RuleRow(String reminderType, String sourceModule, int aheadMinutes, boolean enabled, boolean required) { }

    public record SettingRow(boolean enableExperiment, boolean enableHomework, boolean enableGrade,
                             boolean enableAnnouncement, boolean enableNonCriticalReminder) {
        public static SettingRow defaults() {
            return new SettingRow(true, true, true, true, true);
        }
    }
}
