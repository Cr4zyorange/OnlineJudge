package com.onlinejudge.lab.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab_submission_source_file;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = {
        "file:../database/migrations/20260525_02_create_lab_experiment.sql",
        "file:../database/migrations/20260526_01_create_lab_submission.sql",
        "file:../database/migrations/20260822_02_create_lab_submission_source_file.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class LabSubmissionSourceFileMigrationTest {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LabSubmissionSourceFileMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void sourceFileMigrationCreatesTheFrozenMetadataContract() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT COLUMN_NAME
                  FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 'lab_submission_source_file'
                 ORDER BY ORDINAL_POSITION
                """,
                String.class
        );

        assertThat(columns).containsExactly(
                "id",
                "submission_id",
                "lab_id",
                "course_id",
                "uploader_id",
                "storage_key",
                "original_filename",
                "content_type",
                "file_size",
                "status",
                "created_at",
                "updated_at",
                "deleted_at"
        );
    }

    @Test
    void sourceFileMigrationPersistsOneTrustworthyAssetPerSubmission() {
        Seed seed = seedSubmission(701L, 801L);
        insertSourceFile(seed, "sources/solution.py", "解题源码.py", "text/x-python", 18L, "AVAILABLE");

        SourceFileRow stored = jdbcTemplate.queryForObject(
                """
                SELECT submission_id, lab_id, course_id, uploader_id, storage_key,
                       original_filename, content_type, file_size, status,
                       created_at, updated_at, deleted_at
                  FROM lab_submission_source_file
                 WHERE submission_id = ?
                """,
                (rs, rowNum) -> new SourceFileRow(
                        rs.getLong("submission_id"),
                        rs.getLong("lab_id"),
                        rs.getLong("course_id"),
                        rs.getLong("uploader_id"),
                        rs.getString("storage_key"),
                        rs.getString("original_filename"),
                        rs.getString("content_type"),
                        rs.getLong("file_size"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at"),
                        rs.getTimestamp("deleted_at")
                ),
                seed.submissionId()
        );

        assertThat(stored).isNotNull();
        assertThat(stored.submissionId()).isEqualTo(seed.submissionId());
        assertThat(stored.labId()).isEqualTo(seed.labId());
        assertThat(stored.courseId()).isEqualTo(701L);
        assertThat(stored.uploaderId()).isEqualTo(801L);
        assertThat(stored.storageKey()).isEqualTo("sources/solution.py");
        assertThat(stored.originalFilename()).isEqualTo("解题源码.py");
        assertThat(stored.contentType()).isEqualTo("text/x-python");
        assertThat(stored.fileSize()).isEqualTo(18L);
        assertThat(stored.status()).isEqualTo("AVAILABLE");
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.updatedAt()).isNotNull();
        assertThat(stored.deletedAt()).isNull();

        assertThatThrownBy(() -> insertSourceFile(
                seed,
                "sources/second.py",
                "second.py",
                "text/x-python",
                7L,
                "AVAILABLE"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceFileMigrationRejectsDuplicateStorageKeysAndOrphanSubmissions() {
        Seed first = seedSubmission(702L, 802L);
        Seed second = seedSubmission(702L, 803L);
        insertSourceFile(first, "sources/shared.py", "first.py", "text/x-python", 5L, "AVAILABLE");

        assertThatThrownBy(() -> insertSourceFile(
                second,
                "sources/shared.py",
                "second.py",
                "text/x-python",
                6L,
                "AVAILABLE"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO lab_submission_source_file
                    (submission_id, lab_id, course_id, uploader_id, storage_key,
                     original_filename, content_type, file_size, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                999999L,
                first.labId(),
                first.courseId(),
                first.uploaderId(),
                "sources/orphan.py",
                "orphan.py",
                "text/x-python",
                1L,
                "AVAILABLE",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceFileMigrationRestrictsLifecycleStatusToAvailableOrDeleted() {
        Seed seed = seedSubmission(703L, 804L);

        assertThatThrownBy(() -> insertSourceFile(
                seed,
                "sources/quarantined.py",
                "quarantined.py",
                "text/x-python",
                12L,
                "QUARANTINED"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceFileTableIsSynchronizedAcrossMigrationH2AndComposeSchemas() throws Exception {
        String migrationName = "20260822_02_create_lab_submission_source_file.sql";
        List<String> schemas = List.of(
                Files.readString(Path.of("../database/migrations/" + migrationName)),
                Files.readString(Path.of("../database/mysql/compose-schema.sql"))
        );

        for (String schema : schemas) {
            String normalized = schema.toLowerCase();
            assertThat(normalized)
                    .contains("lab_submission_source_file")
                    .contains("submission_id")
                    .contains("lab_id")
                    .contains("course_id")
                    .contains("uploader_id")
                    .contains("storage_key")
                    .contains("original_filename")
                    .contains("content_type")
                    .contains("file_size")
                    .contains("status")
                    .contains("available")
                    .contains("deleted")
                    .contains("created_at")
                    .contains("updated_at")
                    .contains("deleted_at");
        }

        assertThat(Files.readString(Path.of("src/main/resources/application.properties")))
                .contains("file:../database/migrations/" + migrationName);
        assertThat(Files.readString(Path.of("src/test/resources/application.properties")))
                .contains("file:../database/migrations/" + migrationName);
        assertThat(Files.readString(Path.of("src/main/resources/application.yml")))
                .contains("file:../database/migrations/" + migrationName);
    }

    private Seed seedSubmission(long courseId, long uploaderId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO lab_experiment
                    (course_id, title, description, status, deadline, max_score, allowed_languages,
                     evaluation_mode, auto_evaluate, report_required, time_limit_ms, memory_limit_kb,
                     created_by, deleted, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                courseId,
                "源文件迁移验证",
                "验证受控下载元数据",
                "PUBLISHED",
                Timestamp.valueOf(now.plusDays(1)),
                100,
                "python",
                "DOCKER_IO",
                false,
                false,
                60000,
                262144,
                501L,
                false,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        Long labId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM lab_experiment", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO lab_submission
                    (lab_id, student_id, language, submit_status, evaluation_status, version,
                     is_final, submitted_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                labId,
                uploaderId,
                "python",
                "SUBMITTED",
                "NONE",
                1,
                true,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                false
        );
        Long submissionId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM lab_submission", Long.class);
        return new Seed(labId, submissionId, courseId, uploaderId);
    }

    private void insertSourceFile(
            Seed seed,
            String storageKey,
            String originalFilename,
            String contentType,
            long fileSize,
            String status
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO lab_submission_source_file
                    (submission_id, lab_id, course_id, uploader_id, storage_key,
                     original_filename, content_type, file_size, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                seed.submissionId(),
                seed.labId(),
                seed.courseId(),
                seed.uploaderId(),
                storageKey,
                originalFilename,
                contentType,
                fileSize,
                status,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    private record Seed(long labId, long submissionId, long courseId, long uploaderId) {
    }

    private record SourceFileRow(
            long submissionId,
            long labId,
            long courseId,
            long uploaderId,
            String storageKey,
            String originalFilename,
            String contentType,
            long fileSize,
            String status,
            Timestamp createdAt,
            Timestamp updatedAt,
            Timestamp deletedAt
    ) {
    }
}
