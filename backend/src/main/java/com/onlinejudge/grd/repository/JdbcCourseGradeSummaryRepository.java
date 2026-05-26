package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.PublishStatus;
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
public class JdbcCourseGradeSummaryRepository implements CourseGradeSummaryRepository {
    private static final RowMapper<CourseGradeSummary> ROW_MAPPER = (resultSet, rowNum) -> new CourseGradeSummary(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getLong("student_id"),
            resultSet.getBigDecimal("final_score"),
            FinalStatus.valueOf(resultSet.getString("final_status")),
            PublishStatus.valueOf(resultSet.getString("publish_status")),
            resultSet.getObject("calculation_batch_id", Long.class),
            nullableDateTime(resultSet.getTimestamp("published_at")),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseGradeSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CourseGradeSummary upsert(CourseGradeSummary summary) {
        Optional<CourseGradeSummary> existing = findByStudent(summary.courseId(), summary.studentId());
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE t_course_grade_summary
                    SET final_score = ?,
                        final_status = ?,
                        publish_status = ?,
                        calculation_batch_id = ?,
                        published_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    summary.finalScore(),
                    summary.finalStatus().name(),
                    summary.publishStatus().name(),
                    summary.calculationBatchId(),
                    nullableTimestamp(summary.publishedAt()),
                    Timestamp.valueOf(summary.updatedAt()),
                    existing.get().id()
            );
            return findById(existing.get().id()).orElseThrow();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_course_grade_summary
                    (course_id, student_id, final_score, final_status, publish_status, calculation_batch_id,
                     published_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, summary.courseId());
            statement.setLong(2, summary.studentId());
            statement.setBigDecimal(3, summary.finalScore());
            statement.setString(4, summary.finalStatus().name());
            statement.setString(5, summary.publishStatus().name());
            statement.setObject(6, summary.calculationBatchId());
            statement.setTimestamp(7, nullableTimestamp(summary.publishedAt()));
            statement.setTimestamp(8, Timestamp.valueOf(summary.createdAt()));
            statement.setTimestamp(9, Timestamp.valueOf(summary.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public List<CourseGradeSummary> findByCourseId(long courseId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, final_score, final_status, publish_status,
                               calculation_batch_id, published_at, created_at, updated_at
                        FROM t_course_grade_summary
                        WHERE course_id = ?
                        ORDER BY student_id ASC
                        """,
                ROW_MAPPER,
                courseId
        );
    }

    private Optional<CourseGradeSummary> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, final_score, final_status, publish_status,
                               calculation_batch_id, published_at, created_at, updated_at
                        FROM t_course_grade_summary
                        WHERE id = ?
                        """,
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    private Optional<CourseGradeSummary> findByStudent(long courseId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, final_score, final_status, publish_status,
                               calculation_batch_id, published_at, created_at, updated_at
                        FROM t_course_grade_summary
                        WHERE course_id = ? AND student_id = ?
                        """,
                ROW_MAPPER,
                courseId,
                studentId
        ).stream().findFirst();
    }

    private static Timestamp nullableTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime nullableDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
