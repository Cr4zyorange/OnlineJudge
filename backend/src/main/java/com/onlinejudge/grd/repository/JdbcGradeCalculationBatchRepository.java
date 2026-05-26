package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeCalculationBatch;
import com.onlinejudge.grd.domain.GradeCalculationBatchRepository;
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
public class JdbcGradeCalculationBatchRepository implements GradeCalculationBatchRepository {
    private static final RowMapper<GradeCalculationBatch> ROW_MAPPER = (resultSet, rowNum) -> new GradeCalculationBatch(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getString("trigger_type"),
            resultSet.getInt("affected_item_count"),
            resultSet.getInt("affected_student_count"),
            resultSet.getString("status"),
            resultSet.getString("message"),
            resultSet.getLong("calculated_by"),
            resultSet.getTimestamp("calculated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradeCalculationBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradeCalculationBatch save(GradeCalculationBatch batch) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_calculation_batch
                    (course_id, trigger_type, affected_item_count, affected_student_count, status, message,
                     calculated_by, calculated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, batch.courseId());
            statement.setString(2, batch.triggerType());
            statement.setInt(3, batch.affectedItemCount());
            statement.setInt(4, batch.affectedStudentCount());
            statement.setString(5, batch.status());
            statement.setString(6, batch.message());
            statement.setLong(7, batch.calculatedBy());
            statement.setTimestamp(8, Timestamp.valueOf(batch.calculatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    private Optional<GradeCalculationBatch> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, trigger_type, affected_item_count, affected_student_count,
                               status, message, calculated_by, calculated_at
                        FROM t_grade_calculation_batch
                        WHERE id = ?
                        """,
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }
}
