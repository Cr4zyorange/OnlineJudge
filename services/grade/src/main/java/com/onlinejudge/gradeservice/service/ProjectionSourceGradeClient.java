package com.onlinejudge.gradeservice.service;

import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Normal calculations read only Grade-owned projections; Assessment is never on the request path. */
@Component
public class ProjectionSourceGradeClient implements SourceGradeClient {
    private final JdbcTemplate jdbc;

    public ProjectionSourceGradeClient(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId) {
        Integer gaps = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM grade_source_projection_gap gap
                 WHERE gap.aggregate_id LIKE CONCAT(?, ':', ?, ':%')
                   AND (EXISTS (SELECT 1 FROM grade_source_projection projection
                                WHERE projection.aggregate_id=gap.aggregate_id AND projection.course_id=?)
                        OR NOT EXISTS (SELECT 1 FROM grade_source_projection projection
                                       WHERE projection.aggregate_id=gap.aggregate_id))
                """, Integer.class, sourceType.name(), String.valueOf(sourceId), String.valueOf(courseId));
        if (gaps != null && gaps > 0) {
            throw new SourceProjectionGapException("source grade revision gap requires reconciliation");
        }
        return jdbc.query("""
                SELECT course_id, source_type, source_id, student_id, score, full_score,
                       source_status, updated_at
                  FROM grade_source_projection
                 WHERE course_id=? AND source_type=? AND source_id=?
                 ORDER BY student_id
                """, (rs, ignored) -> new SourceGradeDTO(
                parseId(rs.getString("course_id"), "courseId"),
                SourceGradeType.valueOf(rs.getString("source_type")),
                parseId(rs.getString("source_id"), "sourceId"),
                parseId(rs.getString("student_id"), "studentId"),
                rs.getBigDecimal("score"),
                rs.getBigDecimal("full_score"),
                rs.getString("source_status"),
                rs.getTimestamp("updated_at").toLocalDateTime()
        ), String.valueOf(courseId), sourceType.name(), String.valueOf(sourceId));
    }

    private long parseId(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalidLegacyId) {
            throw new IllegalStateException(field + " is not compatible with the existing numeric GRD API", invalidLegacyId);
        }
    }
}
