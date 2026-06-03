package com.onlinejudge.lrn.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_notification_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "file:../database/migrations/20260603_01_create_lrn_notification.sql")
class NotificationMigrationTest {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    NotificationMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationCreatesNotificationTableWithUserScopedIdempotency() {
        int inserted = jdbcTemplate.update("""
                INSERT INTO lrn_notification
                    (user_id, course_id, idempotency_key, title, content, type, priority, source_module, source_id, action_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 601L, 101L, "event-101", "新作业发布", "请按时完成", "TASK", 2, "HWK", 501L,
                "/courses/101/homeworks/501");

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification
                WHERE user_id = ? AND type = ? AND is_read = FALSE AND deleted_at IS NULL
                """, Integer.class, 601L, "TASK");

        assertThat(inserted).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void migrationCreatesNotificationStatusLogTable() {
        jdbcTemplate.update("""
                INSERT INTO lrn_notification
                    (id, user_id, title, content, type, priority, source_module)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, 701L, 601L, "成绩通知", "成绩已发布", "GRADE", 3, "GRD");

        int inserted = jdbcTemplate.update("""
                INSERT INTO lrn_notification_status_log
                    (notification_id, user_id, old_status, new_status, operation_type)
                VALUES (?, ?, ?, ?, ?)
                """, 701L, 601L, "UNREAD", "READ", "MARK_READ");

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification_status_log
                WHERE notification_id = ?
                  AND user_id = ?
                  AND operation_type = ?
                """, Integer.class, 701L, 601L, "MARK_READ");

        assertThat(inserted).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }
}
