package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
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
public class JdbcGradeRecordRepository implements GradeRecordRepository {
    private static final RowMapper<GradeRecord> ROW_MAPPER = (resultSet, rowNum) -> new GradeRecord(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getLong("student_id"),
            resultSet.getLong("grade_item_id"),
            SourceType.valueOf(resultSet.getString("source_type")),
            resultSet.getObject("source_id", Long.class),
            resultSet.getBigDecimal("raw_score"),
            resultSet.getBigDecimal("weighted_score"),
            GradeStatus.valueOf(resultSet.getString("grade_status")),
            PublishStatus.valueOf(resultSet.getString("publish_status")),
            resultSet.getString("comment"),
            nullableDateTime(resultSet.getTimestamp("source_updated_at")),
            nullableDateTime(resultSet.getTimestamp("calculated_at")),
            nullableDateTime(resultSet.getTimestamp("published_at")),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradeRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradeRecord upsert(GradeRecord record) {
        Optional<GradeRecord> existing = findByStudentAndItem(record.courseId(), record.studentId(), record.gradeItemId());
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE t_grade_record
                    SET source_type = ?,
                        source_id = ?,
                        raw_score = ?,
                        weighted_score = ?,
                        grade_status = ?,
                        comment = ?,
                        source_updated_at = ?,
                        calculated_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    record.sourceType().name(),
                    record.sourceId(),
                    record.rawScore(),
                    record.weightedScore(),
                    record.gradeStatus().name(),
                    record.comment(),
                    nullableTimestamp(record.sourceUpdatedAt()),
                    nullableTimestamp(record.calculatedAt()),
                    Timestamp.valueOf(record.updatedAt()),
                    existing.get().id()
            );
            return findById(existing.get().id()).orElseThrow();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_record
                    (course_id, student_id, grade_item_id, source_type, source_id, raw_score, weighted_score,
                     grade_status, publish_status, comment, source_updated_at, calculated_at, published_at,
                     created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, record.courseId());
            statement.setLong(2, record.studentId());
            statement.setLong(3, record.gradeItemId());
            statement.setString(4, record.sourceType().name());
            statement.setObject(5, record.sourceId());
            statement.setBigDecimal(6, record.rawScore());
            statement.setBigDecimal(7, record.weightedScore());
            statement.setString(8, record.gradeStatus().name());
            statement.setString(9, record.publishStatus().name());
            statement.setString(10, record.comment());
            statement.setTimestamp(11, nullableTimestamp(record.sourceUpdatedAt()));
            statement.setTimestamp(12, nullableTimestamp(record.calculatedAt()));
            statement.setTimestamp(13, nullableTimestamp(record.publishedAt()));
            statement.setTimestamp(14, Timestamp.valueOf(record.createdAt()));
            statement.setTimestamp(15, Timestamp.valueOf(record.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public List<GradeRecord> findByCourseId(long courseId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, grade_item_id, source_type, source_id,
                               raw_score, weighted_score, grade_status, publish_status, comment,
                               source_updated_at, calculated_at, published_at, created_at, updated_at
                        FROM t_grade_record
                        WHERE course_id = ?
                        ORDER BY student_id ASC, grade_item_id ASC
                        """,
                ROW_MAPPER,
                courseId
        );
    }

    private Optional<GradeRecord> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, grade_item_id, source_type, source_id,
                               raw_score, weighted_score, grade_status, publish_status, comment,
                               source_updated_at, calculated_at, published_at, created_at, updated_at
                        FROM t_grade_record
                        WHERE id = ?
                        """,
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    private Optional<GradeRecord> findByStudentAndItem(long courseId, long studentId, long gradeItemId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, grade_item_id, source_type, source_id,
                               raw_score, weighted_score, grade_status, publish_status, comment,
                               source_updated_at, calculated_at, published_at, created_at, updated_at
                        FROM t_grade_record
                        WHERE course_id = ? AND student_id = ? AND grade_item_id = ?
                        """,
                ROW_MAPPER,
                courseId,
                studentId,
                gradeItemId
        ).stream().findFirst();
    }

    private static Timestamp nullableTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime nullableDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
