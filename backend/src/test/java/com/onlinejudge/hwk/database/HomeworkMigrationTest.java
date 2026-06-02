package com.onlinejudge.hwk.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HomeworkMigrationTest {
    private static final Path HOMEWORK_MIGRATION_PATH = Path.of(
            "../database/migrations/20260530_01_create_hwk_homework.sql"
    );
    private static final Path SUBMISSION_MIGRATION_PATH = Path.of(
            "../database/migrations/20260601_01_create_hwk_submission.sql"
    );

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    HomeworkMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void homeworkMigrationUsesMySqlCompatibleConstraintSyntax() throws Exception {
        String migrationSql = Files.readString(HOMEWORK_MIGRATION_PATH);
        String submissionSql = Files.readString(SUBMISSION_MIGRATION_PATH);

        assertThat(migrationSql)
                .doesNotContainPattern("(?i)ADD\\s+CONSTRAINT\\s+IF\\s+NOT\\s+EXISTS");
        assertThat(submissionSql)
                .doesNotContainPattern("(?i)ADD\\s+CONSTRAINT\\s+IF\\s+NOT\\s+EXISTS");
    }

    @Test
    @Sql(scripts = "file:../database/migrations/20260530_01_create_hwk_homework.sql")
    void judgeConfigContractAllowsOnlyOneConfigAndRequiresReferencedHomework() {
        long homeworkId = insertHomework(null);
        long judgeConfigId = insertJudgeConfig(homeworkId);

        assertThatThrownBy(() -> insertJudgeConfig(homeworkId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("UPDATE t_hwk_homework SET judge_config_id = ? WHERE id = ?", judgeConfigId, homeworkId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT judge_config_id FROM t_hwk_homework WHERE id = ?",
                Long.class,
                homeworkId
        )).isEqualTo(judgeConfigId);

        assertThatThrownBy(() -> insertJudgeConfig(999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM t_hwk_homework WHERE id = ?", homeworkId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_judge_config WHERE id = ?",
                Long.class,
                judgeConfigId
        )).isZero();
    }

    @Test
    @Sql(scripts = {
            "file:../database/migrations/20260530_01_create_hwk_homework.sql",
            "file:../database/migrations/20260601_01_create_hwk_submission.sql"
    })
    void submissionContractKeepsDocumentedReviewAndContentColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT LOWER(column_name)
                FROM information_schema.columns
                WHERE table_name = 't_hwk_submission'
                """, String.class);

        assertThat(columns).contains(
                "submit_type",
                "answer_text",
                "answer_json",
                "file_url",
                "language",
                "submit_status",
                "evaluation_status",
                "review_status",
                "auto_score",
                "manual_score",
                "final_score",
                "comment",
                "is_final",
                "submitted_at",
                "reviewed_by",
                "reviewed_at",
                "created_at",
                "updated_at"
        );
    }

    @Test
    @Sql(scripts = {
            "file:../database/migrations/20260530_01_create_hwk_homework.sql",
            "file:../database/migrations/20260601_01_create_hwk_submission.sql"
    })
    void submissionContractRequiresHomeworkAndUniqueStudentVersion() {
        long homeworkId = insertHomework(null);
        insertSubmission(homeworkId, 601L, 1);

        assertThatThrownBy(() -> insertSubmission(homeworkId, 601L, 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertSubmission(999_999L, 601L, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertHomework(Long judgeConfigId) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                (course_id, chapter_id, title, description, type, status, total_score, deadline,
                 allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                 created_by, published_at, is_deleted, created_at, updated_at)
                VALUES (101, NULL, 'migration homework', 'contract check', 'CODE', 'DRAFT', 100,
                        '2026-06-30 23:59:59', 1, 0, 1, ?, 501, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, judgeConfigId);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_homework", Long.class);
    }

    private long insertJudgeConfig(long homeworkId) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_judge_config
                (homework_id, language_limit_json, time_limit_ms, memory_limit_kb,
                 output_compare_mode, created_at, updated_at)
                VALUES (?, '[\"java\"]', 1000, 65536, 'EXACT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, homeworkId);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_judge_config", Long.class);
    }

    private void insertSubmission(long homeworkId, long studentId, int version) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_submission
                (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                 submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                 comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at, is_deleted)
                VALUES (?, ?, 'TEXT', 'answer', NULL, NULL, NULL, 'SUBMITTED', 'NONE', 'UNREVIEWED',
                        NULL, NULL, NULL, NULL, ?, 1, CURRENT_TIMESTAMP, NULL, NULL,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, homeworkId, studentId, version);
    }
}
