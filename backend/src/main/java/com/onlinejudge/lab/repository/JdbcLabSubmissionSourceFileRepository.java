package com.onlinejudge.lab.repository;

import com.onlinejudge.lab.domain.LabSubmissionSourceFile;
import com.onlinejudge.lab.domain.LabSubmissionSourceFileRepository;
import com.onlinejudge.lab.domain.LabSubmissionSourceFileStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcLabSubmissionSourceFileRepository implements LabSubmissionSourceFileRepository {
    private static final RowMapper<LabSubmissionSourceFile> SOURCE_FILE_ROW_MAPPER = (resultSet, rowNum) -> {
        Timestamp deletedAt = resultSet.getTimestamp("deleted_at");
        return new LabSubmissionSourceFile(
                resultSet.getLong("id"),
                resultSet.getLong("submission_id"),
                resultSet.getLong("lab_id"),
                resultSet.getLong("course_id"),
                resultSet.getLong("uploader_id"),
                resultSet.getString("storage_key"),
                resultSet.getString("original_filename"),
                resultSet.getString("content_type"),
                resultSet.getLong("file_size"),
                LabSubmissionSourceFileStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime(),
                deletedAt == null ? null : deletedAt.toLocalDateTime()
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabSubmissionSourceFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabSubmissionSourceFile save(LabSubmissionSourceFile sourceFile) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_submission_source_file
                        (submission_id, lab_id, course_id, uploader_id, storage_key,
                         original_filename, content_type, file_size, status,
                         created_at, updated_at, deleted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, sourceFile.submissionId());
            statement.setLong(2, sourceFile.labId());
            statement.setLong(3, sourceFile.courseId());
            statement.setLong(4, sourceFile.uploaderId());
            statement.setString(5, sourceFile.storageKey());
            statement.setString(6, sourceFile.originalFilename());
            statement.setString(7, sourceFile.contentType());
            statement.setLong(8, sourceFile.fileSize());
            statement.setString(9, sourceFile.status().name());
            statement.setTimestamp(10, Timestamp.valueOf(sourceFile.createdAt()));
            statement.setTimestamp(11, Timestamp.valueOf(sourceFile.updatedAt()));
            statement.setTimestamp(12, sourceFile.deletedAt() == null ? null : Timestamp.valueOf(sourceFile.deletedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("保存提交源文件元数据后无法读取记录"));
    }

    @Override
    public Optional<LabSubmissionSourceFile> findBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, submission_id, lab_id, course_id, uploader_id, storage_key,
                               original_filename, content_type, file_size, status,
                               created_at, updated_at, deleted_at
                          FROM lab_submission_source_file
                         WHERE submission_id = ?
                        """,
                SOURCE_FILE_ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    private Optional<LabSubmissionSourceFile> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, submission_id, lab_id, course_id, uploader_id, storage_key,
                               original_filename, content_type, file_size, status,
                               created_at, updated_at, deleted_at
                          FROM lab_submission_source_file
                         WHERE id = ?
                        """,
                SOURCE_FILE_ROW_MAPPER,
                id
        ).stream().findFirst();
    }
}
