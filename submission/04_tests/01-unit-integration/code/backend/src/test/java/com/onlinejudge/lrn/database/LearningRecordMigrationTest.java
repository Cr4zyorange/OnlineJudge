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
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_record_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "file:../database/migrations/20260602_01_create_lrn_learning_record.sql")
class LearningRecordMigrationTest {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LearningRecordMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationCreatesLearningRecordTableForBehaviorStatistics() {
        int inserted = jdbcTemplate.update("""
                INSERT INTO lrn_learning_record
                    (user_id, course_id, source_module, source_id, action_type, duration, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, 601L, 101L, "CRS", 701L, "ACCESS", 120,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now());

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lrn_learning_record
                WHERE user_id = ? AND course_id = ? AND source_module = ? AND source_id = ?
                """, Integer.class, 601L, 101L, "CRS", 701L);

        assertThat(inserted).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }
}
