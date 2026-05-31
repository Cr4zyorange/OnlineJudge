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
@Sql(scripts = {
        "file:../database/migrations/20260531_01_create_lrn_task_source_tables.sql",
        "file:../database/migrations/20260530_01_create_lrn_learning_task.sql"
})
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

    @Test
    void migrationCreatesDocumentedSourceTablesForAggregatedTaskCenterQueries() {
        int resources = jdbcTemplate.update("""
                INSERT INTO crs_resource
                    (course_id, resource_name, resource_type, file_path, file_size, file_format, upload_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, 101L, "Chapter 1 Slides", 1, "/resources/chapter-1.pdf", 1024L, "pdf", 501L);
        int homework = jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                    (course_id, title, description, type, status, total_score, deadline, created_by, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 101L, "Java Homework 1", "homework description", "FILE", "PUBLISHED",
                100, LocalDateTime.now().plusDays(2), 501L);

        Integer resourceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crs_resource WHERE course_id = ? AND is_deleted = 0",
                Integer.class,
                101L
        );
        Integer homeworkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_homework WHERE course_id = ? AND status = ?",
                Integer.class,
                101L,
                "PUBLISHED"
        );

        assertThat(resources).isEqualTo(1);
        assertThat(homework).isEqualTo(1);
        assertThat(resourceCount).isEqualTo(1);
        assertThat(homeworkCount).isEqualTo(1);
    }
}
