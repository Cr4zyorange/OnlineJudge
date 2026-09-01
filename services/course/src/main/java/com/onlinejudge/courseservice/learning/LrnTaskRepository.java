package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Course-owned learning task facts (LRN folded into Course, #355). */
@Repository
public class LrnTaskRepository {
    private static final String SELECT_TASK = """
            SELECT t.id, t.user_id, t.course_id, t.task_type, t.title, t.deadline, t.progress, t.status,
                   t.action_url, c.course_name
              FROM lrn_learning_task t JOIN crs_course c ON c.id = t.course_id
            """;

    private final JdbcTemplate jdbc;

    public LrnTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TaskRow> recent(long courseId, long userId, int limit) {
        return jdbc.query(SELECT_TASK + """
                WHERE t.course_id = ? AND t.user_id = ?
                 ORDER BY CASE WHEN t.status = 'IN_PROGRESS' THEN 0 ELSE 1 END,
                          t.deadline IS NULL, t.deadline, t.id DESC
                 LIMIT ?
                """, (rs, row) -> task(rs), courseId, userId, limit);
    }

    public List<TaskRow> list(long userId, List<String> taskTypes, String status, Long courseId, String sortBy, String order, int page, int size) {
        StringBuilder sql = new StringBuilder(SELECT_TASK).append(" WHERE t.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (taskTypes != null && !taskTypes.isEmpty()) {
            sql.append(" AND t.task_type IN (").append(String.join(",", taskTypes.stream().map(ignored -> "?").toList())).append(")");
            args.addAll(taskTypes);
        }
        if (status != null && !status.isBlank()) { sql.append(" AND t.status = ?"); args.add(status); }
        if (courseId != null) { sql.append(" AND t.course_id = ?"); args.add(courseId); }
        String column = "createdAt".equalsIgnoreCase(sortBy) ? "t.created_at" : "t.deadline";
        String direction = "asc".equalsIgnoreCase(order) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(column).append(' ').append(direction).append(", t.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), (rs, row) -> task(rs), args.toArray());
    }

    public long count(long userId, List<String> taskTypes, String status, Long courseId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM lrn_learning_task WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (taskTypes != null && !taskTypes.isEmpty()) {
            sql.append(" AND task_type IN (").append(String.join(",", taskTypes.stream().map(ignored -> "?").toList())).append(")");
            args.addAll(taskTypes);
        }
        if (status != null && !status.isBlank()) { sql.append(" AND status = ?"); args.add(status); }
        if (courseId != null) { sql.append(" AND course_id = ?"); args.add(courseId); }
        Long value = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public long insert(long userId, long courseId, String sourceModule, long sourceId, String taskType, String title,
                       LocalDateTime deadline, String actionUrl) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lrn_learning_task
                        (user_id, course_id, source_module, source_id, task_type, title, deadline, status, action_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'NOT_STARTED', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setLong(2, courseId);
            statement.setString(3, sourceModule);
            statement.setLong(4, sourceId);
            statement.setString(5, taskType);
            statement.setString(6, title);
            if (deadline == null) statement.setNull(7, java.sql.Types.TIMESTAMP); else statement.setTimestamp(7, Timestamp.valueOf(deadline));
            statement.setString(8, actionUrl);
            return statement;
        }, keyHolder);
        if (keyHolder.getKeyList().isEmpty()) {
            throw new IllegalStateException("no learning task generated key returned");
        }
        Object value = keyHolder.getKeyList().getFirst().get("id");
        return ((Number) (value == null ? keyHolder.getKeyList().getFirst().values().iterator().next() : value)).longValue();
    }

    public boolean exists(long userId, long courseId, String sourceModule, long sourceId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lrn_learning_task
                 WHERE user_id = ? AND course_id = ? AND source_module = ? AND source_id = ?
                """, Integer.class, userId, courseId, sourceModule, sourceId);
        return count != null && count > 0;
    }

    public void updateProgress(long userId, long courseId, String sourceModule, long sourceId, int percent, String status) {
        jdbc.update("""
                UPDATE lrn_learning_task
                   SET progress = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND course_id = ? AND source_module = ? AND source_id = ?
                """, percent, status, userId, courseId, sourceModule, sourceId);
    }

    public List<Long> activeMemberUserIds(long courseId, String role) {
        String sql = "SELECT user_id FROM crs_course_member WHERE course_id = ? AND join_status = 'ACTIVE' AND is_deleted = FALSE"
                + (role == null ? "" : " AND role = ?");
        List<Long> ids = role == null
                ? jdbc.queryForList(sql, Long.class, courseId)
                : jdbc.queryForList(sql, Long.class, courseId, role);
        return ids == null ? List.of() : ids;
    }

    private TaskRow task(ResultSet rs) throws SQLException {
        Timestamp deadline = rs.getTimestamp("deadline");
        return new TaskRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("course_id"), rs.getString("course_name"),
                rs.getString("task_type"), rs.getString("title"),
                deadline == null ? null : deadline.toLocalDateTime(), rs.getInt("progress"), rs.getString("status"),
                rs.getString("action_url"));
    }

    public record TaskRow(long id, long userId, long courseId, String courseName, String taskType, String title,
                          LocalDateTime deadline, int progress, String status, String actionUrl) { }
}
