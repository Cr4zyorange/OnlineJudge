package com.onlinejudge.lrn.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_reminder_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260605_01_create_lrn_reminder_rule.sql"
})
class ReminderRuleMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesReminderRuleSettingAndScanLogTables() {
        jdbcTemplate.update("""
                INSERT INTO lrn_reminder_rule
                    (user_id, reminder_type, source_module, ahead_minutes, enabled, required)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 601L, "HOMEWORK_DEADLINE", "HWK", 1440, true, false);
        jdbcTemplate.update("""
                INSERT INTO lrn_notification_setting
                    (user_id, enable_experiment, enable_homework, enable_grade, enable_announcement, enable_non_critical_reminder)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 601L, true, false, true, true, false);
        jdbcTemplate.update("""
                INSERT INTO lrn_reminder_scan_log
                    (batch_id, scan_started_at, scan_ended_at, triggered_count, retry_status)
                VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)
                """, "batch-601", 3, "NONE");

        Integer ruleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lrn_reminder_rule", Integer.class);
        Integer settingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lrn_notification_setting", Integer.class);
        Integer scanCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lrn_reminder_scan_log", Integer.class);

        assertThat(ruleCount).isEqualTo(1);
        assertThat(settingCount).isEqualTo(1);
        assertThat(scanCount).isEqualTo(1);
    }
}
