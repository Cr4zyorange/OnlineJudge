package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeChangeLog;
import com.onlinejudge.grd.domain.GradeChangeLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

@Repository
public class JdbcGradeChangeLogRepository implements GradeChangeLogRepository {
    private static final RowMapper<GradeChangeLog> ROW_MAPPER = (resultSet, rowNum) -> new GradeChangeLog(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getLong("student_id"),
            resultSet.getObject("grade_item_id", Long.class),
            resultSet.getString("change_type"),
            resultSet.getBigDecimal("old_value"),
            resultSet.getBigDecimal("new_value"),
            resultSet.getString("reason"),
            resultSet.getLong("operator_id"),
            resultSet.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradeChangeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradeChangeLog save(GradeChangeLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_change_log
                    (course_id, student_id, grade_item_id, change_type, old_value, new_value,
                     reason, operator_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, log.courseId());
            statement.setLong(2, log.studentId());
            statement.setObject(3, log.gradeItemId());
            statement.setString(4, log.changeType());
            statement.setBigDecimal(5, log.oldValue());
            statement.setBigDecimal(6, log.newValue());
            statement.setString(7, log.reason());
            statement.setLong(8, log.operatorId());
            statement.setTimestamp(9, Timestamp.valueOf(log.createdAt()));
            return statement;
        }, keyHolder);
        return log.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public List<GradeChangeLog> findByCourseId(long courseId, Long studentId, Long gradeItemId, int page, int size) {
        int offset = Math.max(page - 1, 0) * size;
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, grade_item_id, change_type, old_value,
                               new_value, reason, operator_id, created_at
                        FROM t_grade_change_log
                        WHERE course_id = ?
                          AND (? IS NULL OR student_id = ?)
                          AND (? IS NULL OR grade_item_id = ?)
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                ROW_MAPPER,
                courseId,
                studentId,
                studentId,
                gradeItemId,
                gradeItemId,
                size,
                offset
        );
    }

    @Override
    public int countByCourseId(long courseId, Long studentId, Long gradeItemId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM t_grade_change_log
                        WHERE course_id = ?
                          AND (? IS NULL OR student_id = ?)
                          AND (? IS NULL OR grade_item_id = ?)
                        """,
                Integer.class,
                courseId,
                studentId,
                studentId,
                gradeItemId,
                gradeItemId
        );
        return count == null ? 0 : count;
    }
}
