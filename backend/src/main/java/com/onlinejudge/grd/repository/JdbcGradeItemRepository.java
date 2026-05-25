package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.SourceType;
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
public class JdbcGradeItemRepository implements GradeItemRepository {
    private static final RowMapper<GradeItem> ROW_MAPPER = (resultSet, rowNum) -> new GradeItem(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getString("name"),
            SourceType.valueOf(resultSet.getString("source_type")),
            resultSet.getObject("source_id", Long.class),
            resultSet.getBigDecimal("full_score"),
            resultSet.getBigDecimal("weight"),
            resultSet.getBoolean("included_in_final"),
            resultSet.getBoolean("enabled"),
            resultSet.getInt("sort_order"),
            resultSet.getLong("created_by"),
            resultSet.getBoolean("deleted"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradeItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradeItem save(GradeItem item) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_item
                    (course_id, name, source_type, source_id, full_score, weight, included_in_final,
                     enabled, sort_order, created_by, deleted, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bindMutableFields(statement, item);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("保存成绩项后无法读取记录"));
    }

    @Override
    public GradeItem update(GradeItem item) {
        int updated = jdbcTemplate.update("""
                UPDATE t_grade_item
                SET name = ?,
                    source_type = ?,
                    source_id = ?,
                    full_score = ?,
                    weight = ?,
                    included_in_final = ?,
                    enabled = ?,
                    sort_order = ?,
                    deleted = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                item.name(),
                item.sourceType().name(),
                item.sourceId(),
                item.fullScore(),
                item.weight(),
                item.includedInFinal(),
                item.enabled(),
                item.sortOrder(),
                item.deleted(),
                Timestamp.valueOf(item.updatedAt()),
                item.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("成绩项不存在");
        }
        return findById(item.id()).orElseThrow(() -> new IllegalStateException("更新成绩项后无法读取记录"));
    }

    @Override
    public Optional<GradeItem> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, name, source_type, source_id, full_score, weight,
                               included_in_final, enabled, sort_order, created_by, deleted, created_at, updated_at
                        FROM t_grade_item
                        WHERE id = ?
                        """,
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public List<GradeItem> findByCourseId(long courseId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, name, source_type, source_id, full_score, weight,
                               included_in_final, enabled, sort_order, created_by, deleted, created_at, updated_at
                        FROM t_grade_item
                        WHERE course_id = ? AND deleted = FALSE
                        ORDER BY sort_order ASC, id ASC
                        """,
                ROW_MAPPER,
                courseId
        );
    }

    private void bindMutableFields(PreparedStatement statement, GradeItem item) throws java.sql.SQLException {
        statement.setLong(1, item.courseId());
        statement.setString(2, item.name());
        statement.setString(3, item.sourceType().name());
        if (item.sourceId() == null) {
            statement.setObject(4, null);
        } else {
            statement.setLong(4, item.sourceId());
        }
        statement.setBigDecimal(5, item.fullScore());
        statement.setBigDecimal(6, item.weight());
        statement.setBoolean(7, item.includedInFinal());
        statement.setBoolean(8, item.enabled());
        statement.setInt(9, item.sortOrder());
        statement.setLong(10, item.createdBy());
        statement.setBoolean(11, item.deleted());
        statement.setTimestamp(12, Timestamp.valueOf(item.createdAt()));
        statement.setTimestamp(13, Timestamp.valueOf(item.updatedAt()));
    }
}
