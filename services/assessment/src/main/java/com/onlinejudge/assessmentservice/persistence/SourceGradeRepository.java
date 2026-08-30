package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class SourceGradeRepository {
    private final JdbcTemplate jdbc;
    public SourceGradeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public long upsertScored(String sourceType, String sourceId, String courseId, String studentId, BigDecimal score, BigDecimal fullScore, Instant now) {
        Long existing = jdbc.query("SELECT source_version FROM assessment_source_grade WHERE source_type=? AND source_id=? AND student_id=?", (rs, ignored) -> rs.getLong(1), sourceType, sourceId, studentId).stream().findFirst().orElse(null);
        long version = existing == null ? 1 : existing + 1;
        if (existing == null) jdbc.update("INSERT INTO assessment_source_grade (source_type, source_id, course_id, student_id, score, full_score, status, source_version, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'SCORED', ?, ?)", sourceType, sourceId, courseId, studentId, score, fullScore, version, Timestamp.from(now));
        else jdbc.update("UPDATE assessment_source_grade SET course_id=?, score=?, full_score=?, status='SCORED', source_version=?, updated_at=? WHERE source_type=? AND source_id=? AND student_id=?", courseId, score, fullScore, version, Timestamp.from(now), sourceType, sourceId, studentId);
        jdbc.update("INSERT INTO assessment_source_grade_snapshot (source_type, source_id, course_id, snapshot_version) VALUES (?, ?, ?, 1) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), snapshot_version=snapshot_version+1", sourceType, sourceId, courseId);
        return version;
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
