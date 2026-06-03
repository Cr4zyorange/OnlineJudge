package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeReviewRequest;
import com.onlinejudge.grd.domain.GradeReviewRequestRepository;
import com.onlinejudge.grd.domain.GradeReviewStatus;
import com.onlinejudge.grd.domain.GradeReviewTargetType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcGradeReviewRequestRepository implements GradeReviewRequestRepository {
    private static final RowMapper<GradeReviewRequest> ROW_MAPPER = (resultSet, rowNum) -> new GradeReviewRequest(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getLong("student_id"),
            resultSet.getObject("grade_item_id", Long.class),
            GradeReviewTargetType.valueOf(resultSet.getString("target_type")),
            resultSet.getString("reason"),
            GradeReviewStatus.valueOf(resultSet.getString("status")),
            resultSet.getBigDecimal("original_score"),
            resultSet.getBigDecimal("adjusted_score"),
            resultSet.getString("response_comment"),
            resultSet.getTimestamp("submitted_at").toLocalDateTime(),
            resultSet.getObject("processed_by", Long.class),
            nullableDateTime(resultSet.getTimestamp("processed_at")),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradeReviewRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradeReviewRequest save(GradeReviewRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_review_request
                    (course_id, student_id, grade_item_id, target_type, reason, status, original_score,
                     adjusted_score, response_comment, submitted_at, processed_by, processed_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, request.courseId());
            statement.setLong(2, request.studentId());
            statement.setObject(3, request.gradeItemId());
            statement.setString(4, request.targetType().name());
            statement.setString(5, request.reason());
            statement.setString(6, request.status().name());
            statement.setBigDecimal(7, request.originalScore());
            statement.setBigDecimal(8, request.adjustedScore());
            statement.setString(9, request.responseComment());
            statement.setTimestamp(10, Timestamp.valueOf(request.submittedAt()));
            statement.setObject(11, request.processedBy());
            statement.setTimestamp(12, nullableTimestamp(request.processedAt()));
            statement.setTimestamp(13, Timestamp.valueOf(request.createdAt()));
            statement.setTimestamp(14, Timestamp.valueOf(request.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public GradeReviewRequest update(GradeReviewRequest request) {
        jdbcTemplate.update("""
                UPDATE t_grade_review_request
                SET status = ?,
                    adjusted_score = ?,
                    response_comment = ?,
                    processed_by = ?,
                    processed_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                request.status().name(),
                request.adjustedScore(),
                request.responseComment(),
                request.processedBy(),
                nullableTimestamp(request.processedAt()),
                Timestamp.valueOf(request.updatedAt()),
                request.id()
        );
        return findById(request.id()).orElseThrow();
    }

    @Override
    public Optional<GradeReviewRequest> findById(long id) {
        return jdbcTemplate.query(selectSql() + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<GradeReviewRequest> findPendingByTarget(
            long courseId,
            long studentId,
            GradeReviewTargetType targetType,
            Long gradeItemId
    ) {
        String gradeItemCondition = gradeItemId == null ? "grade_item_id IS NULL" : "grade_item_id = ?";
        List<Object> args = new ArrayList<>(List.of(courseId, studentId, targetType.name(), GradeReviewStatus.PENDING.name()));
        if (gradeItemId != null) {
            args.add(gradeItemId);
        }
        return jdbcTemplate.query("""
                        SELECT id, course_id, student_id, grade_item_id, target_type, reason, status,
                               original_score, adjusted_score, response_comment, submitted_at,
                               processed_by, processed_at, created_at, updated_at
                        FROM t_grade_review_request
                        WHERE course_id = ? AND student_id = ? AND target_type = ? AND status = ? AND %s
                        ORDER BY submitted_at DESC
                        """.formatted(gradeItemCondition),
                ROW_MAPPER,
                args.toArray()
        ).stream().findFirst();
    }

    @Override
    public List<GradeReviewRequest> findByCourseId(
            long courseId,
            Long studentId,
            Long gradeItemId,
            GradeReviewStatus status,
            int page,
            int size
    ) {
        QueryParts queryParts = queryParts(courseId, studentId, gradeItemId, status);
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(size);
        args.add(Math.max(page - 1, 0) * size);
        return jdbcTemplate.query(selectSql() + queryParts.whereClause() + " ORDER BY submitted_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                args.toArray()
        );
    }

    @Override
    public int countByCourseId(long courseId, Long studentId, Long gradeItemId, GradeReviewStatus status) {
        QueryParts queryParts = queryParts(courseId, studentId, gradeItemId, status);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_grade_review_request" + queryParts.whereClause(),
                Integer.class,
                queryParts.args().toArray()
        );
        return count == null ? 0 : count;
    }

    private static String selectSql() {
        return """
                SELECT id, course_id, student_id, grade_item_id, target_type, reason, status,
                       original_score, adjusted_score, response_comment, submitted_at,
                       processed_by, processed_at, created_at, updated_at
                FROM t_grade_review_request
                """;
    }

    private static QueryParts queryParts(long courseId, Long studentId, Long gradeItemId, GradeReviewStatus status) {
        List<String> conditions = new ArrayList<>(List.of("course_id = ?"));
        List<Object> args = new ArrayList<>(List.of(courseId));
        if (studentId != null) {
            conditions.add("student_id = ?");
            args.add(studentId);
        }
        if (gradeItemId != null) {
            conditions.add("grade_item_id = ?");
            args.add(gradeItemId);
        }
        if (status != null) {
            conditions.add("status = ?");
            args.add(status.name());
        }
        return new QueryParts(" WHERE " + String.join(" AND ", conditions), args);
    }

    private static Timestamp nullableTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime nullableDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record QueryParts(String whereClause, List<Object> args) {
    }
}
