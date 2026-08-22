package com.onlinejudge.hwk.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
    private static final Path EVALUATION_MIGRATION_PATH = Path.of(
            "../database/migrations/20260602_01_create_hwk_evaluation.sql"
    );
    private static final Path REVIEW_LOG_MIGRATION_PATH = Path.of(
            "../database/migrations/20260602_02_create_hwk_review_log.sql"
    );
    private static final Path STATISTICS_ATTENTION_MIGRATION_PATH = Path.of(
            "../database/migrations/20260822_01_add_hwk_statistics_attention_indexes.sql"
    );
    private static final Path H2_STATISTICS_ATTENTION_MIGRATION_PATH = Path.of(
            "src/main/resources/h2-hwk-statistics-attention-indexes.sql"
    );
    private static final Path COMPOSE_MIGRATION_RUNNER_PATH = Path.of(
            "../database/mysql/apply-compose-migration.sh"
    );
    private static final Path DEPLOYMENT_DOCUMENT_PATH = Path.of(
            "../docs/最终提交/部署文档.md"
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
        String evaluationSql = Files.readString(EVALUATION_MIGRATION_PATH);
        String reviewLogSql = Files.readString(REVIEW_LOG_MIGRATION_PATH);

        assertThat(migrationSql)
                .doesNotContainPattern("(?i)ADD\\s+CONSTRAINT\\s+IF\\s+NOT\\s+EXISTS");
        assertThat(submissionSql)
                .doesNotContainPattern("(?i)ADD\\s+CONSTRAINT\\s+IF\\s+NOT\\s+EXISTS");
        assertThat(evaluationSql)
                .doesNotContainPattern("(?i)ADD\\s+CONSTRAINT\\s+IF\\s+NOT\\s+EXISTS");
        assertThat(reviewLogSql)
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

    @Test
    @Sql(scripts = {
            "file:../database/migrations/20260530_01_create_hwk_homework.sql",
            "file:../database/migrations/20260601_01_create_hwk_submission.sql"
    })
    void freshSubmissionSchemaContainsStatisticsAndAttentionCompositeIndexes() {
        assertIndexColumns(
                "idx_hwk_submission_effective",
                "homework_id", "is_final", "is_deleted", "submit_status", "student_id"
        );
        assertIndexColumns(
                "idx_hwk_submission_attention",
                "homework_id", "is_final", "is_deleted", "submitted_at", "id",
                "submit_status", "student_id", "submit_type", "evaluation_status", "review_status"
        );
    }

    @Test
    @Sql(scripts = {
            "file:../database/migrations/20260530_01_create_hwk_homework.sql",
            "file:../database/migrations/20260601_01_create_hwk_submission.sql"
    })
    void h2StatisticsAttentionIndexUpgradeIsIdempotent() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_hwk_submission_effective");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_hwk_submission_attention");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource(H2_STATISTICS_ATTENTION_MIGRATION_PATH)
        );

        populator.execute(jdbcTemplate.getDataSource());
        populator.execute(jdbcTemplate.getDataSource());

        assertIndexColumns(
                "idx_hwk_submission_effective",
                "homework_id", "is_final", "is_deleted", "submit_status", "student_id"
        );
        assertIndexColumns(
                "idx_hwk_submission_attention",
                "homework_id", "is_final", "is_deleted", "submitted_at", "id",
                "submit_status", "student_id", "submit_type", "evaluation_status", "review_status"
        );
    }

    @Test
    void mysqlStatisticsAttentionUpgradeIsGuardedAndAddsBothMissingIndexesAtomically() throws Exception {
        String migrationSql = Files.readString(STATISTICS_ATTENTION_MIGRATION_PATH);

        assertThat(migrationSql)
                .contains("information_schema.statistics")
                .contains("index_name = 'idx_hwk_submission_effective'")
                .contains("index_name = 'idx_hwk_submission_attention'")
                .contains("ELSEIF effective_index_count = 0")
                .contains("ELSEIF attention_index_count = 0")
                .containsPattern("(?is)ALTER\\s+TABLE\\s+t_hwk_submission\\s+"
                        + "ADD\\s+INDEX\\s+idx_hwk_submission_effective.*?,\\s*"
                        + "ADD\\s+INDEX\\s+idx_hwk_submission_attention")
                .containsPattern("(?is)ADD\\s+INDEX\\s+idx_hwk_submission_attention\\s*\\(\\s*"
                        + "homework_id\\s*,\\s*is_final\\s*,\\s*is_deleted\\s*,\\s*"
                        + "submitted_at\\s*,\\s*id\\s*,\\s*submit_status\\s*,\\s*student_id\\s*,\\s*"
                        + "submit_type\\s*,\\s*evaluation_status\\s*,\\s*review_status\\s*\\)")
                .doesNotContainPattern("(?i)CREATE\\s+INDEX");
    }

    @Test
    void composeRetainedVolumeHasAnExplicitVersionedMigrationEntryPoint() throws Exception {
        String runner = Files.readString(COMPOSE_MIGRATION_RUNNER_PATH);
        String deploymentDocument = Files.readString(DEPLOYMENT_DOCUMENT_PATH);

        assertThat(runner)
                .contains("database/migrations")
                .contains("docker compose")
                .contains("exec -T mysql");
        assertThat(COMPOSE_MIGRATION_RUNNER_PATH).isExecutable();
        assertThat(deploymentDocument)
                .contains("apply-compose-migration.sh")
                .contains("20260822_01_add_hwk_statistics_attention_indexes.sql");
    }

    @Test
    @Sql(scripts = {
            "file:../database/migrations/20260530_01_create_hwk_homework.sql",
            "file:../database/migrations/20260601_01_create_hwk_submission.sql",
            "file:../database/migrations/20260602_01_create_hwk_evaluation.sql"
    })
    void evaluationContractStoresDocumentedTraceableResultAndRequiresSubmission() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT LOWER(column_name)
                FROM information_schema.columns
                WHERE table_name = 't_hwk_evaluation'
                """, String.class);

        assertThat(columns).contains(
                "submission_id",
                "homework_id",
                "student_id",
                "evaluation_type",
                "status",
                "score",
                "passed_cases",
                "total_cases",
                "time_used_ms",
                "memory_used_kb",
                "feedback",
                "log_url",
                "started_at",
                "finished_at"
        );

        long homeworkId = insertHomework(null);
        long submissionId = insertSubmission(homeworkId, 601L, 1);
        insertEvaluation(submissionId, homeworkId, 601L, "CODE_JUDGE");
        insertEvaluation(submissionId, homeworkId, 601L, "REJUDGE");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_evaluation WHERE submission_id = ?",
                Integer.class,
                submissionId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT evaluation_type FROM t_hwk_evaluation WHERE submission_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                submissionId
        )).isEqualTo("REJUDGE");
        assertThatThrownBy(() -> insertEvaluation(999_999L, homeworkId, 601L, "CODE_JUDGE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Sql(scripts = {
            "file:../database/migrations/20260530_01_create_hwk_homework.sql",
            "file:../database/migrations/20260601_01_create_hwk_submission.sql",
            "file:../database/migrations/20260602_02_create_hwk_review_log.sql"
    })
    void reviewLogContractStoresRejudgeAuditAndRequiresSubmissionAndHomework() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT LOWER(column_name)
                FROM information_schema.columns
                WHERE table_name = 't_hwk_review_log'
                """, String.class);

        assertThat(columns).contains(
                "submission_id",
                "homework_id",
                "student_id",
                "operation_type",
                "old_score",
                "new_score",
                "comment",
                "operator_id",
                "reason",
                "created_at"
        );

        long homeworkId = insertHomework(null);
        long submissionId = insertSubmission(homeworkId, 601L, 1);
        insertReviewLog(submissionId, homeworkId, 601L, 501L, "teacher requested new judge");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM t_hwk_review_log WHERE submission_id = ?",
                String.class,
                submissionId
        )).isEqualTo("teacher requested new judge");
        assertThatThrownBy(() -> insertReviewLog(999_999L, homeworkId, 601L, 501L, "bad submission"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReviewLog(submissionId, 999_999L, 601L, 501L, "bad homework"))
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

    private void assertIndexColumns(String indexName, String... expectedColumns) {
        List<String> columns = jdbcTemplate.queryForList("""
                        SELECT LOWER(column_name)
                        FROM information_schema.index_columns
                        WHERE LOWER(table_name) = 't_hwk_submission'
                          AND LOWER(index_name) = ?
                        ORDER BY ordinal_position
                        """,
                String.class,
                indexName.toLowerCase()
        );
        assertThat(columns).containsExactly(expectedColumns);
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

    private long insertSubmission(long homeworkId, long studentId, int version) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_submission
                (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                 submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                 comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at, is_deleted)
                VALUES (?, ?, 'TEXT', 'answer', NULL, NULL, NULL, 'SUBMITTED', 'NONE', 'UNREVIEWED',
                        NULL, NULL, NULL, NULL, ?, 1, CURRENT_TIMESTAMP, NULL, NULL,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, homeworkId, studentId, version);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_submission", Long.class);
    }

    private void insertEvaluation(long submissionId, long homeworkId, long studentId, String evaluationType) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_evaluation
                (submission_id, homework_id, student_id, evaluation_type, status, score, passed_cases, total_cases,
                 time_used_ms, memory_used_kb, feedback, log_url, started_at, finished_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACCEPTED', 100, 2, 2, 120, 1024, 'all cases passed',
                        '/logs/hwk/evaluation/1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, submissionId, homeworkId, studentId, evaluationType);
    }

    private void insertReviewLog(long submissionId, long homeworkId, long studentId, long operatorId, String reason) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_review_log
                (submission_id, homework_id, student_id, operation_type, old_score, new_score,
                 comment, operator_id, reason, created_at)
                VALUES (?, ?, ?, 'REJUDGE', 40, 100, NULL, ?, ?, CURRENT_TIMESTAMP)
                """, submissionId, homeworkId, studentId, operatorId, reason);
    }
}
