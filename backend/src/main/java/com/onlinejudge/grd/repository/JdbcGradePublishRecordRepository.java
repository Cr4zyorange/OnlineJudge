package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradePublishRecord;
import com.onlinejudge.grd.domain.GradePublishRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcGradePublishRecordRepository implements GradePublishRecordRepository {
    private static final RowMapper<GradePublishRecord> ROW_MAPPER = (resultSet, rowNum) -> new GradePublishRecord(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getString("idempotency_key"),
            resultSet.getString("publish_scope"),
            resultSet.getInt("published_count"),
            resultSet.getLong("published_by"),
            resultSet.getTimestamp("published_at").toLocalDateTime(),
            resultSet.getString("notification_status"),
            resultSet.getString("remark")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradePublishRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradePublishRecord save(GradePublishRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_publish_record
                    (course_id, idempotency_key, publish_scope, published_count, published_by, published_at, notification_status, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, record.courseId());
            statement.setString(2, record.idempotencyKey());
            statement.setString(3, record.publishScope());
            statement.setInt(4, record.publishedCount());
            statement.setLong(5, record.publishedBy());
            statement.setTimestamp(6, Timestamp.valueOf(record.publishedAt()));
            statement.setString(7, record.notificationStatus());
            statement.setString(8, record.remark());
            return statement;
        }, keyHolder);
        return record.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public List<GradePublishRecord> findByCourseId(long courseId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return jdbcTemplate.query("""
                        SELECT id, course_id, idempotency_key, publish_scope, published_count, published_by,
                               published_at, notification_status, remark
                        FROM t_grade_publish_record
                        WHERE course_id = ?
                        ORDER BY published_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                ROW_MAPPER,
                courseId,
                normalizedSize,
                (normalizedPage - 1) * normalizedSize
        );
    }

    @Override
    public int countByCourseId(long courseId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_grade_publish_record WHERE course_id = ?",
                Integer.class,
                courseId
        );
        return total == null ? 0 : total;
    }

    @Override
    public Optional<GradePublishRecord> findLatestByCourseId(long courseId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, idempotency_key, publish_scope, published_count, published_by,
                               published_at, notification_status, remark
                        FROM t_grade_publish_record
                        WHERE course_id = ?
                        ORDER BY published_at DESC, id DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                courseId
        ).stream().findFirst();
    }

    @Override
    public Optional<GradePublishRecord> findByIdempotencyKey(long courseId, String idempotencyKey) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, idempotency_key, publish_scope, published_count, published_by,
                               published_at, notification_status, remark
                        FROM t_grade_publish_record
                        WHERE course_id = ? AND idempotency_key = ?
                        """,
                ROW_MAPPER,
                courseId,
                idempotencyKey
        ).stream().findFirst();
    }
}
