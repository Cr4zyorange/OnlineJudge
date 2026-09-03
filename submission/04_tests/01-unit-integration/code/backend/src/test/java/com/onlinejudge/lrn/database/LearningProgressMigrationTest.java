package com.onlinejudge.lrn.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_progress_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "file:../database/migrations/20260531_01_create_lrn_learning_progress.sql")
class LearningProgressMigrationTest {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LearningProgressMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationCreatesLearningProgressTableWithResumeUniqueKey() {
        int inserted = jdbcTemplate.update("""
                INSERT INTO lrn_learning_progress
                    (user_id, course_id, chapter_id, source_module, source_id, progress_percent, last_position, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, 601L, 101L, 1001L, "CRS", 701L, 65, "video_play_time=1234", "IN_PROGRESS");

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lrn_learning_progress
                WHERE user_id = ? AND course_id = ? AND source_module = ? AND source_id = ?
                """, Integer.class, 601L, 101L, "CRS", 701L);

        assertThat(inserted).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }
}
