package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Repository
public class SourceGradeRepository {
    private final JdbcTemplate jdbc;
    public SourceGradeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    public long upsertScored(String sourceType, String sourceId, String courseId, String studentId, BigDecimal score, BigDecimal fullScore, Instant now) {
        long snapshotVersion = nextSnapshotVersion(sourceType, sourceId, courseId);
        jdbc.update("INSERT INTO assessment_source_grade (source_type, source_id, course_id, student_id, score, full_score, status, source_version, snapshot_version, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'SCORED', 1, ?, ?) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), score=VALUES(score), full_score=VALUES(full_score), status='SCORED', source_version=source_version+1, snapshot_version=VALUES(snapshot_version), updated_at=VALUES(updated_at)", sourceType, sourceId, courseId, studentId, score, fullScore, snapshotVersion, Timestamp.from(now));
        SourceGrade grade = findCurrent(sourceType, sourceId, studentId);
        appendRevision(grade, snapshotVersion);
        return grade.sourceVersion();
    }
    @Transactional
    public Optional<SourceGrade> markUngradedIfPresent(String sourceType, String sourceId, String studentId,
                                                       Instant now) {
        String courseId = jdbc.query("""
                SELECT course_id
                  FROM assessment_source_grade
                 WHERE source_type = ? AND source_id = ? AND student_id = ?
                """, (rs, ignored) -> rs.getString("course_id"), sourceType, sourceId, studentId).stream().findFirst()
                .orElse(null);
        if (courseId == null) return Optional.empty();
        // Always lock the source-wide snapshot before an individual student row.  This is
        // the same order as upsertScored, so independent student updates cannot deadlock.
        long snapshotVersion = nextSnapshotVersion(sourceType, sourceId, courseId);
        int updated = jdbc.update("""
                UPDATE assessment_source_grade
                   SET score = NULL, status = 'UNGRADED', source_version = source_version + 1,
                       snapshot_version = ?, updated_at = ?
                 WHERE source_type = ? AND source_id = ? AND student_id = ?
                """, snapshotVersion, Timestamp.from(now), sourceType, sourceId, studentId);
        if (updated != 1) throw new IllegalStateException("source grade disappeared while marking it ungraded");
        SourceGrade grade = findCurrent(sourceType, sourceId, studentId);
        appendRevision(grade, snapshotVersion);
        return Optional.of(grade);
    }
    public long snapshotVersion(String courseId, String sourceType, String sourceId) {
        return jdbc.query("SELECT snapshot_version FROM assessment_source_grade_snapshot WHERE course_id=? AND source_type=? AND source_id=?", (rs, ignored) -> rs.getLong(1), courseId, sourceType, sourceId).stream().findFirst().orElse(1L);
    }
    /** A snapshot older than this cannot be reconstructed after retention or a schema upgrade. */
    public OptionalLong firstRetainedSnapshotVersion(String courseId, String sourceType, String sourceId) {
        Long sourceFloor = jdbc.query("""
                SELECT first_reconstructable_version
                  FROM assessment_source_grade_snapshot
                 WHERE course_id=? AND source_type=? AND source_id=?
                """, (rs, ignored) -> rs.getLong(1), courseId, sourceType, sourceId)
                .stream().findFirst().orElse(null);
        List<Long> versions = jdbc.query("""
                SELECT MIN(snapshot_version) FROM assessment_source_grade_revision
                 WHERE course_id=? AND source_type=? AND source_id=?
                """, (rs, ignored) -> rs.getObject(1, Long.class), courseId, sourceType, sourceId);
        Long firstRevision = versions.isEmpty() ? null : versions.getFirst();
        if (firstRevision == null) {
            // A restart while the upgrade backfill is unavailable must fail closed:
            // accepting an earlier token would turn a non-empty Grade projection into
            // an apparently valid empty page.
            long current = snapshotVersion(courseId, sourceType, sourceId);
            return sourceFloor == null ? OptionalLong.of(current) : OptionalLong.of(Math.max(sourceFloor, current));
        }
        return OptionalLong.of(sourceFloor == null ? firstRevision : Math.max(sourceFloor, firstRevision));
    }
    public long count(String courseId, String sourceType, String sourceId, long snapshotVersion) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT student_id, MAX(snapshot_version) AS selected_version
                      FROM assessment_source_grade_revision
                     WHERE course_id=? AND source_type=? AND source_id=? AND snapshot_version <= ?
                     GROUP BY student_id
                ) revisions
                """, Long.class, courseId, sourceType, sourceId, snapshotVersion);
    }
    public List<SourceGrade> page(String courseId, String sourceType, String sourceId, long snapshotVersion, int offset, int size) {
        return jdbc.query("""
                SELECT revision.course_id, revision.source_type, revision.source_id, revision.student_id,
                       revision.score, revision.full_score, revision.status, revision.source_version,
                       revision.snapshot_version, revision.updated_at
                  FROM assessment_source_grade_revision revision
                  JOIN (
                    SELECT student_id, MAX(snapshot_version) AS selected_version
                      FROM assessment_source_grade_revision
                     WHERE course_id=? AND source_type=? AND source_id=? AND snapshot_version <= ?
                     GROUP BY student_id
                  ) selected ON selected.student_id = revision.student_id
                           AND selected.selected_version = revision.snapshot_version
                 WHERE revision.course_id=? AND revision.source_type=? AND revision.source_id=?
                 ORDER BY revision.student_id ASC LIMIT ? OFFSET ?
                """, (rs, ignored) -> sourceGrade(rs), courseId, sourceType, sourceId, snapshotVersion,
                courseId, sourceType, sourceId, size, offset);
    }
    private long nextSnapshotVersion(String sourceType, String sourceId, String courseId) {
        jdbc.update("INSERT INTO assessment_source_grade_snapshot (source_type, source_id, course_id, snapshot_version) VALUES (?, ?, ?, 1) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), snapshot_version=snapshot_version+1", sourceType, sourceId, courseId);
        return jdbc.queryForObject("SELECT snapshot_version FROM assessment_source_grade_snapshot WHERE source_type=? AND source_id=? FOR UPDATE", Long.class, sourceType, sourceId);
    }
    private SourceGrade findCurrent(String sourceType, String sourceId, String studentId) {
        return jdbc.query("SELECT course_id, source_type, source_id, student_id, score, full_score, status, source_version, snapshot_version, updated_at FROM assessment_source_grade WHERE source_type=? AND source_id=? AND student_id=? FOR UPDATE", (rs, ignored) -> sourceGrade(rs), sourceType, sourceId, studentId).stream().findFirst().orElseThrow();
    }
    private void appendRevision(SourceGrade grade, long snapshotVersion) {
        jdbc.update("INSERT INTO assessment_source_grade_revision (source_type, source_id, course_id, student_id, snapshot_version, score, full_score, status, source_version, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", grade.sourceType(), grade.sourceId(), grade.courseId(), grade.studentId(), snapshotVersion, grade.score(), grade.fullScore(), grade.status(), grade.sourceVersion(), Timestamp.from(grade.updatedAt()));
    }
    private SourceGrade sourceGrade(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SourceGrade(rs.getString("course_id"), rs.getString("source_type"), rs.getString("source_id"), rs.getString("student_id"), rs.getBigDecimal("score"), rs.getBigDecimal("full_score"), rs.getString("status"), rs.getLong("source_version"), rs.getLong("snapshot_version"), rs.getTimestamp("updated_at").toInstant());
    }
    public record SourceGrade(String courseId, String sourceType, String sourceId, String studentId, BigDecimal score, BigDecimal fullScore, String status, long sourceVersion, long snapshotVersion, Instant updatedAt) { }
}
