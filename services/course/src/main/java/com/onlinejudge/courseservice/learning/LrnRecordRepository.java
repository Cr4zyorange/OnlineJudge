package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Course-owned learning record facts (LRN folded into Course, #355). */
@Repository
public class LrnRecordRepository {
    private final JdbcTemplate jdbc;

    public LrnRecordRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long insert(long userId, long courseId, String sourceModule, long sourceId, String actionType,
                       int duration, LocalDateTime startedAt, LocalDateTime endedAt) {
        jdbc.update("""
                INSERT INTO lrn_learning_record
                    (user_id, course_id, source_module, source_id, action_type, duration, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, courseId, sourceModule, sourceId, actionType, duration,
                Timestamp.valueOf(startedAt), Timestamp.valueOf(endedAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM lrn_learning_record WHERE user_id = ?", Long.class, userId);
    }

    public Summary summary(long userId, Long courseId) {
        String where = " WHERE user_id = ?" + (courseId == null ? "" : " AND course_id = ?");
        List<Object> args = courseId == null ? List.of(userId) : List.of(userId, courseId);
        return jdbc.query("""
                SELECT COALESCE(SUM(duration), 0) AS total_duration,
                       SUM(CASE WHEN action_type = 'ACCESS' THEN 1 ELSE 0 END) AS access_count,
                       SUM(CASE WHEN action_type = 'COMPLETE' THEN 1 ELSE 0 END) AS complete_count,
                       SUM(CASE WHEN action_type = 'SUBMIT' THEN 1 ELSE 0 END) AS submit_count,
                       COUNT(*) AS record_count
                  FROM lrn_learning_record
                """ + where, rs -> rs.next() ? new Summary(
                rs.getLong("total_duration"), rs.getLong("access_count"), rs.getLong("complete_count"),
                rs.getLong("submit_count"), rs.getLong("record_count")) : Summary.ZERO, args.toArray());
    }

    public List<RecordRow> recent(long userId, Long courseId, int limit) {
        String sql = """
                SELECT r.id, r.course_id, r.source_module, r.source_id, r.action_type, r.duration,
                       r.started_at, r.ended_at, c.course_name
                  FROM lrn_learning_record r JOIN crs_course c ON c.id = r.course_id
                 WHERE r.user_id = ?
                """ + (courseId == null ? "" : " AND r.course_id = ?")
                + " ORDER BY r.started_at DESC, r.id DESC LIMIT ?";
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        if (courseId != null) args.add(courseId);
        args.add(limit);
        return jdbc.query(sql, (rs, row) -> record(rs), args.toArray());
    }

    public Optional<RecordRow> findById(long userId, long id) {
        return jdbc.query("""
                SELECT r.id, r.course_id, r.source_module, r.source_id, r.action_type, r.duration,
                       r.started_at, r.ended_at, c.course_name
                  FROM lrn_learning_record r JOIN crs_course c ON c.id = r.course_id
                 WHERE r.user_id = ? AND r.id = ?
                """, rs -> rs.next() ? Optional.of(record(rs)) : Optional.empty(), userId, id);
    }

    public List<TrendRow> trends(long userId, Long courseId, int days) {
        String sql = """
                SELECT CAST(r.started_at AS DATE) AS trend_day,
                       COALESCE(SUM(r.duration), 0) AS total_duration,
                       SUM(CASE WHEN r.action_type = 'ACCESS' THEN 1 ELSE 0 END) AS access_count,
                       SUM(CASE WHEN r.action_type = 'COMPLETE' THEN 1 ELSE 0 END) AS complete_count
                  FROM lrn_learning_record r
                 WHERE r.user_id = ? AND r.started_at >= ?
                """ + (courseId == null ? "" : " AND r.course_id = ?")
                + " GROUP BY CAST(r.started_at AS DATE) ORDER BY trend_day";
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        args.add(Timestamp.valueOf(LocalDateTime.now().minusDays(days)));
        if (courseId != null) args.add(courseId);
        return jdbc.query(sql, (rs, row) -> new TrendRow(rs.getDate("trend_day").toLocalDate(),
                rs.getLong("total_duration"), rs.getLong("access_count"), rs.getLong("complete_count")), args.toArray());
    }

    private RecordRow record(ResultSet rs) throws SQLException {
        return new RecordRow(rs.getLong("id"), rs.getLong("course_id"), rs.getString("course_name"),
                rs.getString("source_module"), rs.getLong("source_id"), rs.getString("action_type"), rs.getInt("duration"),
                rs.getTimestamp("started_at").toLocalDateTime(), rs.getTimestamp("ended_at").toLocalDateTime());
    }

    public record Summary(long totalDurationSeconds, long resourceAccessCount, long completedTaskCount,
                          long submittedTaskCount, long totalRecordCount) {
        static final Summary ZERO = new Summary(0, 0, 0, 0, 0);
    }

    public record RecordRow(long id, long courseId, String courseName, String sourceModule, long sourceId,
                            String actionType, int durationSeconds, LocalDateTime startedAt, LocalDateTime endedAt) { }

    public record TrendRow(LocalDate date, long durationSeconds, long resourceAccessCount, long completedTaskCount) { }
}
