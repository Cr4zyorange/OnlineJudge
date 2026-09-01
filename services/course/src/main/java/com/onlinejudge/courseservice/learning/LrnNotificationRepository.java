package com.onlinejudge.courseservice.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Course-owned notification facts (LRN folded into Course, #355). */
@Repository
public class LrnNotificationRepository {
    private final JdbcTemplate jdbc;

    public LrnNotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<NotificationRow> findByUser(long userId, String type, Boolean isRead, LocalDateTime startTime,
                                            LocalDateTime endTime, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id, course_id, title, content, type, priority, is_read, source_module, source_id,
                       action_url, created_at, read_at
                  FROM lrn_notification WHERE user_id = ? AND deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (type != null) { sql.append(" AND type = ?"); args.add(type); }
        if (isRead != null) { sql.append(" AND is_read = ?"); args.add(isRead); }
        if (startTime != null) { sql.append(" AND created_at >= ?"); args.add(Timestamp.valueOf(startTime)); }
        if (endTime != null) { sql.append(" AND created_at <= ?"); args.add(Timestamp.valueOf(endTime)); }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), (rs, row) -> row(rs), args.toArray());
    }

    public long countByUser(long userId, String type, Boolean isRead, LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM lrn_notification WHERE user_id = ? AND deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (type != null) { sql.append(" AND type = ?"); args.add(type); }
        if (isRead != null) { sql.append(" AND is_read = ?"); args.add(isRead); }
        if (startTime != null) { sql.append(" AND created_at >= ?"); args.add(Timestamp.valueOf(startTime)); }
        if (endTime != null) { sql.append(" AND created_at <= ?"); args.add(Timestamp.valueOf(endTime)); }
        Long value = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public long countUnread(long userId) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lrn_notification
                 WHERE user_id = ? AND is_read = FALSE AND deleted_at IS NULL
                """, Long.class, userId);
        return value == null ? 0 : value;
    }

    public int markRead(long userId, List<Long> notificationIds, boolean readAll) {
        if (readAll) {
            return jdbc.update("""
                    UPDATE lrn_notification SET is_read = TRUE, read_at = CURRENT_TIMESTAMP
                     WHERE user_id = ? AND deleted_at IS NULL AND is_read = FALSE
                    """, userId);
        }
        if (notificationIds.isEmpty()) return 0;
        String placeholders = String.join(",", notificationIds.stream().map(ignored -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(notificationIds);
        return jdbc.update("""
                UPDATE lrn_notification SET is_read = TRUE, read_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND deleted_at IS NULL AND id IN (""" + placeholders + ")", args.toArray());
    }

    public int deleteForUser(long userId, long notificationId) {
        return jdbc.update("""
                UPDATE lrn_notification SET deleted_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND id = ? AND deleted_at IS NULL
                """, userId, notificationId);
    }

    public Optional<Long> save(long userId, Long courseId, String idempotencyKey, String title, String content, String type,
                               int priority, String sourceModule, Long sourceId, String actionUrl) {
        if (idempotencyKey != null) {
            Integer existing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM lrn_notification
                     WHERE idempotency_key = ? AND user_id = ?
                    """, Integer.class, idempotencyKey, userId);
            if (existing != null && existing > 0) return Optional.empty();
        }
        jdbc.update("""
                INSERT INTO lrn_notification
                    (user_id, course_id, idempotency_key, title, content, type, priority, source_module, source_id, action_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, courseId, idempotencyKey, title, content, type, priority, sourceModule, sourceId, actionUrl);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM lrn_notification WHERE user_id = ?", Long.class, userId);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public boolean isActiveCourseMember(long userId, long courseId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM crs_course_member
                 WHERE course_id = ? AND user_id = ? AND join_status = 'ACTIVE' AND is_deleted = FALSE
                """, Integer.class, courseId, userId);
        return count != null && count > 0;
    }

    public List<Long> activeMemberUserIds(long courseId, String role) {
        String sql = "SELECT user_id FROM crs_course_member WHERE course_id = ? AND join_status = 'ACTIVE' AND is_deleted = FALSE"
                + (role == null ? "" : " AND role = ?");
        List<Long> ids = role == null
                ? jdbc.queryForList(sql, Long.class, courseId)
                : jdbc.queryForList(sql, Long.class, courseId, role);
        return ids == null ? List.of() : ids;
    }

    private NotificationRow row(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp readAt = rs.getTimestamp("read_at");
        return new NotificationRow(rs.getLong("id"), nullableLong(rs, "course_id"), rs.getString("title"), rs.getString("content"),
                rs.getString("type"), rs.getInt("priority"), rs.getBoolean("is_read"), rs.getString("source_module"),
                nullableLong(rs, "source_id"), rs.getString("action_url"),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                readAt == null ? null : readAt.toLocalDateTime());
    }

    private Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    public record NotificationRow(long id, Long courseId, String title, String content, String type, int priority,
                                  boolean read, String sourceModule, Long sourceId, String actionUrl,
                                  LocalDateTime createdAt, LocalDateTime readAt) { }
}
