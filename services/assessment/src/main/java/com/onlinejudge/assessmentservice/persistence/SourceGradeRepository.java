package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class SourceGradeRepository {
    private final JdbcTemplate jdbc;
    public SourceGradeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public long upsertScored(String sourceType, String sourceId, String courseId, String studentId, BigDecimal score, BigDecimal fullScore, Instant now) {
        Long existing = jdbc.query("SELECT source_version FROM assessment_source_grade WHERE source_type=? AND source_id=? AND student_id=?", (rs, ignored) -> rs.getLong(1), sourceType, sourceId, studentId).stream().findFirst().orElse(null);
        long version = existing == null ? 1 : existing + 1;
        if (existing == null) jdbc.update("INSERT INTO assessment_source_grade (source_type, source_id, course_id, student_id, score, full_score, status, source_version, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'SCORED', ?, ?)", sourceType, sourceId, courseId, studentId, score, fullScore, version, Timestamp.from(now));
        else jdbc.update("UPDATE assessment_source_grade SET course_id=?, score=?, full_score=?, status='SCORED', source_version=?, updated_at=? WHERE source_type=? AND source_id=? AND student_id=?", courseId, score, fullScore, version, Timestamp.from(now), sourceType, sourceId, studentId);
        return version;
    }
}
