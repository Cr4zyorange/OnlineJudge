package com.onlinejudge.lrn.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_task_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "file:../database/migrations/20260530_01_create_lrn_learning_task.sql")
class LearningTaskMigrationTest {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LearningTaskMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationCreatesLearningTaskSnapshotTableForTaskCenterQueries() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);
        int inserted = jdbcTemplate.update("""
                INSERT INTO lrn_learning_task
                    (user_id, course_id, source_module, source_id, task_type, title, deadline, progress, status, action_url, snapshot_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 601L, 101L, "HWK", 501L, "HOMEWORK", "Java作业1", deadline, 10, "IN_PROGRESS",
                "/courses/101/homeworks/501");

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lrn_learning_task
                WHERE user_id = ? AND course_id = ? AND task_type = ? AND status = ?
                """, Integer.class, 601L, 101L, "HOMEWORK", "IN_PROGRESS");

        assertThat(inserted).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }
}
