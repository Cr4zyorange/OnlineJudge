package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachment;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcHomeworkSubmissionAttachmentRepository implements HomeworkSubmissionAttachmentRepository {
    private static final String SELECT_COLUMNS = """
            id, public_id, submission_id, homework_id, course_id, uploader_id, storage_key,
            original_filename, content_type, file_size, status, active_slot, expires_at, bound_at,
            created_at, updated_at, deleted_at
            """;

    private static final RowMapper<HomeworkSubmissionAttachment> ROW_MAPPER = (resultSet, rowNum) ->
            new HomeworkSubmissionAttachment(
                    resultSet.getLong("id"),
                    resultSet.getString("public_id"),
                    resultSet.getObject("submission_id", Long.class),
                    resultSet.getLong("homework_id"),
                    resultSet.getLong("course_id"),
                    resultSet.getLong("uploader_id"),
                    resultSet.getString("storage_key"),
                    resultSet.getString("original_filename"),
                    resultSet.getString("content_type"),
                    resultSet.getLong("file_size"),
                    HomeworkSubmissionAttachmentStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("active_slot", Integer.class),
                    toLocalDateTime(resultSet.getTimestamp("expires_at")),
                    toLocalDateTime(resultSet.getTimestamp("bound_at")),
                    resultSet.getTimestamp("created_at").toLocalDateTime(),
                    resultSet.getTimestamp("updated_at").toLocalDateTime(),
                    toLocalDateTime(resultSet.getTimestamp("deleted_at"))
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkSubmissionAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HomeworkSubmissionAttachment save(HomeworkSubmissionAttachment attachment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_hwk_submission_attachment
                        (public_id, submission_id, homework_id, course_id, uploader_id, storage_key,
                         original_filename, content_type, file_size, status, active_slot, expires_at,
                         bound_at, created_at, updated_at, deleted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, attachment.publicId());
            statement.setObject(2, attachment.submissionId());
            statement.setLong(3, attachment.homeworkId());
            statement.setLong(4, attachment.courseId());
            statement.setLong(5, attachment.uploaderId());
            statement.setString(6, attachment.storageKey());
            statement.setString(7, attachment.originalFilename());
            statement.setString(8, attachment.contentType());
            statement.setLong(9, attachment.fileSize());
            statement.setString(10, attachment.status().name());
            statement.setObject(11, attachment.activeSlot());
            statement.setObject(12, timestamp(attachment.expiresAt()));
            statement.setObject(13, timestamp(attachment.boundAt()));
            statement.setTimestamp(14, Timestamp.valueOf(attachment.createdAt()));
            statement.setTimestamp(15, Timestamp.valueOf(attachment.updatedAt()));
            statement.setObject(16, timestamp(attachment.deletedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("failed to reload homework attachment"));
    }

    @Override
    public Optional<HomeworkSubmissionAttachment> findByPublicId(String publicId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM t_hwk_submission_attachment WHERE public_id = ?",
                ROW_MAPPER,
                publicId
        ).stream().findFirst();
    }

    @Override
    public Optional<HomeworkSubmissionAttachment> findByPublicIdForUpdate(String publicId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM t_hwk_submission_attachment WHERE public_id = ? FOR UPDATE",
                ROW_MAPPER,
                publicId
        ).stream().findFirst();
    }

    @Override
    public Optional<HomeworkSubmissionAttachment> findActiveUploadedForUpdate(long homeworkId, long uploaderId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + """
                         FROM t_hwk_submission_attachment
                        WHERE homework_id = ? AND uploader_id = ? AND active_slot = 1
                        FOR UPDATE
                        """,
                ROW_MAPPER,
                homeworkId,
                uploaderId
        ).stream().findFirst();
    }

    @Override
    public Optional<HomeworkSubmissionAttachment> findBySubmissionId(long submissionId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM t_hwk_submission_attachment WHERE submission_id = ?",
                ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    @Override
    public List<HomeworkSubmissionAttachment> findExpiredUploadedForUpdate(LocalDateTime now, int limit) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + """
                         FROM t_hwk_submission_attachment
                        WHERE status = 'UPLOADED' AND expires_at <= ?
                        ORDER BY expires_at ASC, id ASC
                        LIMIT ? FOR UPDATE
                        """,
                ROW_MAPPER,
                Timestamp.valueOf(now),
                limit
        );
    }

    @Override
    public List<HomeworkSubmissionAttachment> findDeletedForUpdate(int limit) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + """
                         FROM t_hwk_submission_attachment
                        WHERE status = 'DELETED'
                        ORDER BY deleted_at ASC, id ASC
                        LIMIT ? FOR UPDATE
                        """,
                ROW_MAPPER,
                limit
        );
    }

    @Override
    public boolean bind(long id, long submissionId, LocalDateTime boundAt) {
        return jdbcTemplate.update("""
                UPDATE t_hwk_submission_attachment
                   SET submission_id = ?, status = 'BOUND', expires_at = NULL, bound_at = ?,
                       active_slot = NULL, updated_at = ?, deleted_at = NULL
                 WHERE id = ? AND status = 'UPLOADED' AND submission_id IS NULL AND expires_at > ?
                """,
                submissionId,
                Timestamp.valueOf(boundAt),
                Timestamp.valueOf(boundAt),
                id,
                Timestamp.valueOf(boundAt)
        ) == 1;
    }

    @Override
    public boolean markDeleted(long id, LocalDateTime deletedAt) {
        return jdbcTemplate.update("""
                UPDATE t_hwk_submission_attachment
                   SET status = 'DELETED', submission_id = NULL, expires_at = NULL, bound_at = NULL,
                       active_slot = NULL, updated_at = ?, deleted_at = ?
                 WHERE id = ? AND status = 'UPLOADED' AND submission_id IS NULL
                """,
                Timestamp.valueOf(deletedAt),
                Timestamp.valueOf(deletedAt),
                id
        ) == 1;
    }

    @Override
    public boolean purgeDeleted(long id) {
        return jdbcTemplate.update(
                "DELETE FROM t_hwk_submission_attachment WHERE id = ? AND status = 'DELETED'",
                id
        ) == 1;
    }

    private Optional<HomeworkSubmissionAttachment> findById(long id) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM t_hwk_submission_attachment WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
