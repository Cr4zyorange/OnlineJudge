package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SourceGradeRepository {
    private final JdbcTemplate jdbc;
    public SourceGradeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    public long upsertScored(String sourceType, String sourceId, String courseId, String studentId, BigDecimal score, BigDecimal fullScore, Instant now) {
        // MySQL's duplicate-key path takes the aggregate row lock before it increments.
        // A select-then-update loses one increment when two worker transactions read vN together.
        jdbc.update("INSERT INTO assessment_source_grade (source_type, source_id, course_id, student_id, score, full_score, status, source_version, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'SCORED', 1, ?) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), score=VALUES(score), full_score=VALUES(full_score), status='SCORED', source_version=source_version+1, updated_at=VALUES(updated_at)", sourceType, sourceId, courseId, studentId, score, fullScore, Timestamp.from(now));
        long version = jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type=? AND source_id=? AND student_id=? FOR UPDATE", Long.class, sourceType, sourceId, studentId);
        jdbc.update("INSERT INTO assessment_source_grade_snapshot (source_type, source_id, course_id, snapshot_version) VALUES (?, ?, ?, 1) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), snapshot_version=snapshot_version+1", sourceType, sourceId, courseId);
        return version;
    }
    @Transactional
    public Optional<SourceGrade> markUngradedIfPresent(String sourceType, String sourceId, String studentId,
                                                       Instant now) {
        int updated = jdbc.update("""
                UPDATE assessment_source_grade
                   SET score = NULL, status = 'UNGRADED', source_version = source_version + 1, updated_at = ?
                 WHERE source_type = ? AND source_id = ? AND student_id = ?
                """, Timestamp.from(now), sourceType, sourceId, studentId);
        if (updated == 0) return Optional.empty();
        SourceGrade grade = jdbc.query("""
                SELECT course_id, source_type, source_id, student_id, score, full_score, status,
                       source_version, updated_at
                  FROM assessment_source_grade
                 WHERE source_type = ? AND source_id = ? AND student_id = ?
                   FOR UPDATE
                """, (rs, ignored) -> new SourceGrade(rs.getString("course_id"), rs.getString("source_type"),
                rs.getString("source_id"), rs.getString("student_id"), rs.getBigDecimal("score"),
                rs.getBigDecimal("full_score"), rs.getString("status"), rs.getLong("source_version"),
                rs.getTimestamp("updated_at").toInstant()), sourceType, sourceId, studentId).stream().findFirst()
                .orElseThrow();
        jdbc.update("""
                INSERT INTO assessment_source_grade_snapshot (source_type, source_id, course_id, snapshot_version)
                VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), snapshot_version=snapshot_version+1
                """, sourceType, sourceId, grade.courseId());
        return Optional.of(grade);
    }
    public long snapshotVersion(String courseId, String sourceType, String sourceId) {
        return jdbc.query("SELECT snapshot_version FROM assessment_source_grade_snapshot WHERE course_id=? AND source_type=? AND source_id=?", (rs, ignored) -> rs.getLong(1), courseId, sourceType, sourceId).stream().findFirst().orElse(1L);
    }
    public long count(String courseId, String sourceType, String sourceId, long snapshotVersion) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade WHERE course_id=? AND source_type=? AND source_id=? AND source_version <= ?", Long.class, courseId, sourceType, sourceId, snapshotVersion);
    }
    public List<SourceGrade> page(String courseId, String sourceType, String sourceId, long snapshotVersion, int offset, int size) {
        return jdbc.query("SELECT course_id, source_type, source_id, student_id, score, full_score, status, source_version, updated_at FROM assessment_source_grade WHERE course_id=? AND source_type=? AND source_id=? AND source_version <= ? ORDER BY student_id ASC LIMIT ? OFFSET ?",
                (rs, ignored) -> new SourceGrade(rs.getString("course_id"), rs.getString("source_type"), rs.getString("source_id"), rs.getString("student_id"), rs.getBigDecimal("score"), rs.getBigDecimal("full_score"), rs.getString("status"), rs.getLong("source_version"), rs.getTimestamp("updated_at").toInstant()), courseId, sourceType, sourceId, snapshotVersion, size, offset);
    }
    public record SourceGrade(String courseId, String sourceType, String sourceId, String studentId, BigDecimal score, BigDecimal fullScore, String status, long sourceVersion, Instant updatedAt) { }
}
