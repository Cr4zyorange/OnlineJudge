package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Course-owned learning progress facts (LRN folded into Course, #355). */
@Repository
public class LrnProgressRepository {
    private final JdbcTemplate jdbc;

    public LrnProgressRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void upsert(long userId, long courseId, Long chapterId, String sourceModule, long sourceId,
                       int progressPercent, String lastPosition, String status) {
        int updated = jdbc.update("""
                UPDATE lrn_learning_progress
                   SET chapter_id = ?, progress_percent = ?, last_position = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND course_id = ? AND source_module = ? AND source_id = ?
                """, chapterId, progressPercent, lastPosition, status, userId, courseId, sourceModule, sourceId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO lrn_learning_progress
                        (user_id, course_id, chapter_id, source_module, source_id, progress_percent, last_position, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, userId, courseId, chapterId, sourceModule, sourceId, progressPercent, lastPosition, status);
        }
    }

    public List<ProgressRow> listByUser(long userId, Long courseId) {
        String sql = """
                SELECT p.id, p.user_id, p.course_id, p.chapter_id, p.source_module, p.source_id, p.progress_percent,
                       p.last_position, p.status, p.updated_at, c.course_name, ch.chapter_name
                  FROM lrn_learning_progress p
                  JOIN crs_course c ON c.id = p.course_id
                  LEFT JOIN crs_chapter ch ON ch.id = p.chapter_id
                 WHERE p.user_id = ?
                """ + (courseId == null ? "" : " AND p.course_id = ?")
                + " ORDER BY p.course_id, p.chapter_id, p.source_module, p.source_id";
        return courseId == null
                ? jdbc.query(sql, (rs, row) -> progress(rs), userId)
                : jdbc.query(sql, (rs, row) -> progress(rs), userId, courseId);
    }

    public List<ProgressRow> listByCourse(long courseId) {
        return jdbc.query("""
                SELECT p.id, p.user_id, p.course_id, p.chapter_id, p.source_module, p.source_id, p.progress_percent,
                       p.last_position, p.status, p.updated_at, c.course_name, ch.chapter_name
                  FROM lrn_learning_progress p
                  JOIN crs_course c ON c.id = p.course_id
                  LEFT JOIN crs_chapter ch ON ch.id = p.chapter_id
                 WHERE p.course_id = ?
                 ORDER BY p.user_id, p.chapter_id, p.source_module, p.source_id
                """, (rs, row) -> progress(rs), courseId);
    }

    private ProgressRow progress(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new ProgressRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("course_id"), rs.getString("course_name"),
                nullableLong(rs, "chapter_id"), rs.getString("chapter_name"), rs.getString("source_module"), rs.getLong("source_id"),
                rs.getInt("progress_percent"), rs.getString("last_position"), rs.getString("status"),
                updatedAt == null ? null : updatedAt.toLocalDateTime());
    }

    private Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    public record ProgressRow(long id, long userId, long courseId, String courseName, Long chapterId, String chapterName,
                              String sourceModule, long sourceId, int progressPercent, String lastPosition, String status,
                              LocalDateTime updatedAt) { }
}
