package com.onlinejudge.hwk.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_submission_attachment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = {
        "file:../database/migrations/20260530_01_create_hwk_homework.sql",
        "file:../database/migrations/20260601_01_create_hwk_submission.sql",
        "file:../database/migrations/20260822_03_create_hwk_submission_attachment.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class HomeworkSubmissionAttachmentMigrationTest {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired
    HomeworkSubmissionAttachmentMigrationTest(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Test
    void attachmentMigrationCreatesTheFrozenUploadAndBindingContract() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT COLUMN_NAME
                  FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 't_hwk_submission_attachment'
                 ORDER BY ORDINAL_POSITION
                """,
                String.class
        );

        assertThat(columns).containsExactly(
                "id",
                "public_id",
                "submission_id",
                "homework_id",
                "course_id",
                "uploader_id",
                "storage_key",
                "original_filename",
                "content_type",
                "file_size",
                "status",
                "active_slot",
                "expires_at",
                "bound_at",
                "created_at",
                "updated_at",
                "deleted_at"
        );
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 't_hwk_submission_attachment' AND COLUMN_NAME = 'active_slot'
                """,
                String.class
        )).isEqualToIgnoringCase("TINYINT");
    }

    @Test
    void attachmentMigrationSupportsOneOpaqueUploadAtomicallyBoundToOneSubmission() {
        Seed seed = seedHomeworkAndSubmission(101L, 601L);
        insertAttachment(
                "9afde3b0-7e3a-4fb0-9b9a-2aa4b57ef101",
                null,
                seed,
                "homework/answer.pdf",
                18L,
                "UPLOADED",
                LocalDateTime.now().plusHours(24),
                null
        );

        jdbcTemplate.update(
                """
                UPDATE t_hwk_submission_attachment
                   SET submission_id = ?, status = 'BOUND', active_slot = NULL, expires_at = NULL,
                       bound_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE public_id = ?
                """,
                seed.submissionId(),
                "9afde3b0-7e3a-4fb0-9b9a-2aa4b57ef101"
        );

        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT public_id, submission_id, homework_id, course_id, uploader_id,
                       storage_key, original_filename, content_type, file_size, status,
                       expires_at, bound_at, deleted_at
                  FROM t_hwk_submission_attachment
                 WHERE submission_id = ?
                """,
                seed.submissionId()
        )).containsEntry("PUBLIC_ID", "9afde3b0-7e3a-4fb0-9b9a-2aa4b57ef101")
                .containsEntry("SUBMISSION_ID", seed.submissionId())
                .containsEntry("HOMEWORK_ID", seed.homeworkId())
                .containsEntry("COURSE_ID", 101L)
                .containsEntry("UPLOADER_ID", 601L)
                .containsEntry("STORAGE_KEY", "homework/answer.pdf")
                .containsEntry("ORIGINAL_FILENAME", "作业答案.pdf")
                .containsEntry("CONTENT_TYPE", "application/pdf")
                .containsEntry("FILE_SIZE", 18L)
                .containsEntry("STATUS", "BOUND");
    }

    @Test
    void attachmentMigrationRejectsDuplicatePublicStorageAndSubmissionBindings() {
        Seed first = seedHomeworkAndSubmission(102L, 602L);
        Seed second = seedHomeworkAndSubmission(102L, 603L);
        insertAttachment("file-public-1", first.submissionId(), first, "homework/shared.pdf", 8L,
                "BOUND", null, LocalDateTime.now());

        assertThatThrownBy(() -> insertAttachment(
                "file-public-1", null, second, "homework/second.pdf", 8L,
                "UPLOADED", LocalDateTime.now().plusHours(24), null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertAttachment(
                "file-public-2", null, second, "homework/shared.pdf", 8L,
                "UPLOADED", LocalDateTime.now().plusHours(24), null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertAttachment(
                "file-public-3", first.submissionId(), first, "homework/third.pdf", 8L,
                "BOUND", null, LocalDateTime.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attachmentMigrationAllowsOnlyOneActiveUploadPerStudentAndHomework() {
        Seed seed = seedHomeworkAndSubmission(108L, 608L);
        insertActiveAttachment(
                "active-file-1",
                seed,
                "homework/active-1.pdf",
                1
        );
        assertThatThrownBy(() -> insertActiveAttachment(
                "active-file-2",
                seed,
                "homework/active-2.pdf",
                1
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE t_hwk_submission_attachment SET active_slot = 2 WHERE public_id = 'active-file-1'"
        )).isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                UPDATE t_hwk_submission_attachment
                   SET status = 'DELETED', active_slot = NULL, expires_at = NULL,
                       deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE public_id = 'active-file-1'
                """);
        assertThatCode(() -> insertActiveAttachment(
                "active-file-2",
                seed,
                "homework/active-2.pdf",
                1
        )).doesNotThrowAnyException();
    }

    @Test
    void attachmentMigrationEnforcesPositiveSizeForeignKeysAndLifecycleStates() {
        Seed seed = seedHomeworkAndSubmission(103L, 604L);

        assertThatThrownBy(() -> insertAttachment(
                "zero-size", null, seed, "homework/empty.pdf", 0L,
                "UPLOADED", LocalDateTime.now().plusHours(24), null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttachment(
                "invalid-status", null, seed, "homework/quarantined.pdf", 1L,
                "QUARANTINED", LocalDateTime.now().plusHours(24), null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttachment(
                "bound-without-submission", null, seed, "homework/unbound.pdf", 1L,
                "BOUND", null, LocalDateTime.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttachment(
                "uploaded-with-submission", seed.submissionId(), seed, "homework/uploaded.pdf", 1L,
                "UPLOADED", LocalDateTime.now().plusHours(24), null
        )).isInstanceOf(DataIntegrityViolationException.class);

        Seed orphan = new Seed(999999L, 999999L, seed.courseId(), seed.uploaderId());
        assertThatThrownBy(() -> insertAttachment(
                "orphan", null, orphan, "homework/orphan.pdf", 1L,
                "UPLOADED", LocalDateTime.now().plusHours(24), null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attachmentMigrationProvidesLookupAndOrphanCleanupIndexes() {
        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT INDEX_NAME
                  FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                 WHERE TABLE_NAME = 't_hwk_submission_attachment'
                """,
                String.class
        );

        assertThat(indexes).contains(
                "idx_hwk_attachment_homework",
                "idx_hwk_attachment_course",
                "idx_hwk_attachment_uploader",
                "idx_hwk_attachment_cleanup",
                "idx_hwk_attachment_deleted_cleanup"
        );

        assertThat(indexColumns("idx_hwk_attachment_cleanup"))
                .containsExactly("status", "expires_at");
        assertThat(indexColumns("idx_hwk_attachment_deleted_cleanup"))
                .containsExactly("status", "deleted_at");
        assertThat(constraintColumns("uk_hwk_attachment_active_slot"))
                .containsExactly("homework_id", "uploader_id", "active_slot");

        List<String> uniqueConstraints = jdbcTemplate.queryForList(
                """
                SELECT CONSTRAINT_NAME
                  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                 WHERE TABLE_NAME = 't_hwk_submission_attachment'
                   AND CONSTRAINT_TYPE = 'UNIQUE'
                """,
                String.class
        );
        assertThat(uniqueConstraints).contains(
                "uk_hwk_attachment_public_id",
                "uk_hwk_attachment_storage_key",
                "uk_hwk_attachment_submission",
                "uk_hwk_attachment_active_slot"
        );
    }

    @Test
    void attachmentMigrationCanBeAppliedTwiceWithoutDuplicateIndexFailure() {
        ResourceDatabasePopulator migration = new ResourceDatabasePopulator(
                new FileSystemResource("../database/migrations/20260822_03_create_hwk_submission_attachment.sql")
        );

        assertThatCode(() -> migration.execute(dataSource)).doesNotThrowAnyException();
    }

    @Test
    void attachmentTableIsSynchronizedAcrossMigrationH2AndComposeSchemas() throws Exception {
        String migrationName = "20260822_03_create_hwk_submission_attachment.sql";
        List<String> schemas = List.of(
                Files.readString(Path.of("../database/migrations/" + migrationName)),
                Files.readString(Path.of("../database/mysql/compose-schema.sql"))
        );

        for (String schema : schemas) {
            String normalized = schema.toLowerCase();
            assertThat(normalized)
                    .contains("t_hwk_submission_attachment")
                    .contains("public_id")
                    .contains("submission_id")
                    .contains("homework_id")
                    .contains("course_id")
                    .contains("uploader_id")
                    .contains("storage_key")
                    .contains("original_filename")
                    .contains("content_type")
                    .contains("file_size")
                    .contains("active_slot")
                    .contains("uploaded")
                    .contains("bound")
                    .contains("deleted")
                    .contains("expires_at")
                    .contains("bound_at")
                    .contains("deleted_at");
        }

        assertThat(Files.readString(Path.of("src/main/resources/application.properties")))
                .contains("file:../database/migrations/" + migrationName);
        assertThat(Files.readString(Path.of("src/test/resources/application.properties")))
                .contains("file:../database/migrations/" + migrationName);
        assertThat(Files.readString(Path.of("src/main/resources/application.yml")))
                .contains("file:../database/migrations/" + migrationName);
    }

    private Seed seedHomeworkAndSubmission(long courseId, long uploaderId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO t_hwk_homework
                    (course_id, title, type, status, total_score, deadline, allow_resubmit,
                     allow_late_submit, show_evaluation_before_publish, created_by, published_at,
                     is_deleted, created_at, updated_at)
                VALUES (?, ?, 'FILE', 'PUBLISHED', 100, ?, TRUE, FALSE, FALSE, 501, ?, FALSE, ?, ?)
                """,
                courseId,
                "附件迁移验证",
                Timestamp.valueOf(now.plusDays(1)),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        Long homeworkId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_homework", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO t_hwk_submission
                    (homework_id, student_id, submit_type, submit_status, evaluation_status,
                     review_status, version, is_final, submitted_at, created_at, updated_at, is_deleted)
                VALUES (?, ?, 'FILE', 'SUBMITTED', 'NONE', 'UNREVIEWED', 1, TRUE, ?, ?, ?, FALSE)
                """,
                homeworkId,
                uploaderId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        Long submissionId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_submission", Long.class);
        return new Seed(homeworkId, submissionId, courseId, uploaderId);
    }

    private void insertAttachment(
            String publicId,
            Long submissionId,
            Seed seed,
            String storageKey,
            long fileSize,
            String status,
            LocalDateTime expiresAt,
            LocalDateTime boundAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        Integer activeSlot = "UPLOADED".equals(status) ? 1 : null;
        jdbcTemplate.update(
                """
                INSERT INTO t_hwk_submission_attachment
                    (public_id, submission_id, homework_id, course_id, uploader_id, storage_key,
                     original_filename, content_type, file_size, status, active_slot, expires_at,
                     bound_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, '作业答案.pdf', 'application/pdf', ?, ?, ?, ?, ?, ?, ?)
                """,
                publicId,
                submissionId,
                seed.homeworkId(),
                seed.courseId(),
                seed.uploaderId(),
                storageKey,
                fileSize,
                status,
                activeSlot,
                expiresAt == null ? null : Timestamp.valueOf(expiresAt),
                boundAt == null ? null : Timestamp.valueOf(boundAt),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    private void insertActiveAttachment(
            String publicId,
            Seed seed,
            String storageKey,
            int activeSlot
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO t_hwk_submission_attachment
                    (public_id, submission_id, homework_id, course_id, uploader_id, storage_key,
                     original_filename, content_type, file_size, status, active_slot, expires_at,
                     bound_at, created_at, updated_at)
                VALUES (?, NULL, ?, ?, ?, ?, 'active.pdf', 'application/pdf', 8,
                        'UPLOADED', ?, ?, NULL, ?, ?)
                """,
                publicId,
                seed.homeworkId(),
                seed.courseId(),
                seed.uploaderId(),
                storageKey,
                activeSlot,
                Timestamp.valueOf(now.plusHours(24)),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    private List<String> indexColumns(String indexName) {
        return jdbcTemplate.queryForList(
                """
                SELECT COLUMN_NAME
                  FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                 WHERE TABLE_NAME = 't_hwk_submission_attachment' AND INDEX_NAME = ?
                 ORDER BY ORDINAL_POSITION
                """,
                String.class,
                indexName
        );
    }

    private List<String> constraintColumns(String constraintName) {
        return jdbcTemplate.queryForList(
                """
                SELECT COLUMN_NAME
                  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                 WHERE TABLE_NAME = 't_hwk_submission_attachment' AND CONSTRAINT_NAME = ?
                 ORDER BY ORDINAL_POSITION
                """,
                String.class,
                constraintName
        );
    }

    private record Seed(long homeworkId, long submissionId, long courseId, long uploaderId) {
    }
}
