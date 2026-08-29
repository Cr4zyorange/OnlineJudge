package com.onlinejudge.lrn.repository;

import com.onlinejudge.integration.learning.LearningCourseClient;
import com.onlinejudge.lrn.domain.Notification;
import com.onlinejudge.lrn.service.NotificationCreateCommand;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcNotificationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final LearningCourseClient courseClient;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate, LearningCourseClient courseClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.courseClient = courseClient;
    }

    public boolean isActiveCourseMember(long userId, long courseId) {
        return courseClient.isActiveMember(userId, courseId);
    }

    public Optional<Long> save(long userId, String type, NotificationCreateCommand command, String idempotencyKey) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO lrn_notification
                        (user_id, course_id, idempotency_key, title, content, type, priority, source_module, source_id, action_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    userId,
                    command.courseId(),
                    idempotencyKey,
                    command.title(),
                    command.content(),
                    type,
                    command.priority(),
                    command.sourceModule(),
                    command.sourceId(),
                    command.actionUrl());
            Long id = jdbcTemplate.queryForObject("""
                    SELECT id
                    FROM lrn_notification
                    WHERE user_id = ?
                      AND idempotency_key = ?
                    ORDER BY id DESC
                    LIMIT 1
                    """, Long.class, userId, idempotencyKey);
            if (id != null) {
                insertStatusLog(id, userId, null, "UNREAD", "CREATE");
            }
            return id == null ? Optional.empty() : Optional.of(id);
        } catch (DuplicateKeyException ignored) {
            return Optional.empty();
        }
    }

    public List<Notification> findByUser(long userId, String type, Boolean isRead,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size) {
        QueryParts query = notificationFilter(userId, type, isRead, startTime, endTime);
        Set<Long> visibleCourses = Set.copyOf(courseClient.findActiveCourseIds(userId));
        List<Notification> visible = jdbcTemplate.query("""
                SELECT id, user_id, course_id, title, content, type, priority, is_read,
                       source_module, source_id, action_url, created_at, read_at
                FROM lrn_notification notification
                """ + query.whereClause() + """
                ORDER BY created_at DESC, id DESC
                """, this::mapRow, query.args().toArray()).stream()
                .filter(notification -> visible(notification.courseId(), visibleCourses)).toList();
        int from = Math.min(Math.max(0, (page - 1) * size), visible.size());
        return visible.subList(from, Math.min(from + size, visible.size()));
    }

    public long countByUser(long userId, String type, Boolean isRead, LocalDateTime startTime, LocalDateTime endTime) {
        QueryParts query = notificationFilter(userId, type, isRead, startTime, endTime);
        Set<Long> visibleCourses = Set.copyOf(courseClient.findActiveCourseIds(userId));
        return jdbcTemplate.query("""
                SELECT id,user_id,course_id,title,content,type,priority,is_read,source_module,source_id,action_url,created_at,read_at
                FROM lrn_notification notification
                """ + query.whereClause(), this::mapRow, query.args().toArray()).stream()
                .filter(notification -> visible(notification.courseId(), visibleCourses)).count();
    }

    public long countUnread(long userId) {
        return countByUser(userId, null, false, null, null);
    }

    public int markRead(long userId, List<Long> notificationIds, boolean readAll) {
        List<StatusTarget> targets = findUnreadTargets(userId, notificationIds, readAll);
        int updated = 0;
        for (StatusTarget target : targets) {
            int rows = jdbcTemplate.update("""
                    UPDATE lrn_notification
                    SET is_read = TRUE,
                        read_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND user_id = ?
                      AND is_read = FALSE
                      AND deleted_at IS NULL
                    """, target.notificationId(), userId);
            if (rows > 0) {
                insertStatusLog(target.notificationId(), userId, target.status(), "READ", "MARK_READ");
                updated += rows;
            }
        }
        return updated;
    }

    public Optional<Integer> deleteForUser(long userId, long notificationId) {
        Optional<StatusTarget> target = findDeletableTarget(userId, notificationId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        int rows = jdbcTemplate.update("""
                UPDATE lrn_notification
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND user_id = ?
                  AND deleted_at IS NULL
                """, notificationId, userId);
        if (rows > 0) {
            insertStatusLog(notificationId, userId, target.get().status(), "DELETED", "DELETE");
        }
        return Optional.of(rows);
    }

    private QueryParts notificationFilter(long userId, String type, Boolean isRead,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder where = new StringBuilder("""
                WHERE notification.user_id = ?
                  AND notification.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (type != null) {
            where.append(" AND notification.type = ?");
            args.add(type);
        }
        if (isRead != null) {
            where.append(" AND notification.is_read = ?");
            args.add(isRead);
        }
        if (startTime != null) {
            where.append(" AND notification.created_at >= ?");
            args.add(startTime);
        }
        if (endTime != null) {
            where.append(" AND notification.created_at <= ?");
            args.add(endTime);
        }
        return new QueryParts(where.toString(), args);
    }

    private List<StatusTarget> findUnreadTargets(long userId, List<Long> notificationIds, boolean readAll) {
        if (!readAll && notificationIds.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String idFilter = "";
        if (!readAll) {
            idFilter = " AND notification.id IN (" + placeholders(notificationIds.size()) + ")";
            args.addAll(notificationIds);
        }
        Set<Long> visibleCourses = Set.copyOf(courseClient.findActiveCourseIds(userId));
        return jdbcTemplate.query("""
                SELECT notification.id,notification.course_id,
                       CASE WHEN notification.is_read = TRUE THEN 'READ' ELSE 'UNREAD' END AS current_status
                FROM lrn_notification notification
                """ + visibleNotificationWhere() + """
                  AND notification.is_read = FALSE
                """ + idFilter + """
                ORDER BY notification.id
                """, (rs, rowNum) -> new StatusTarget(
                rs.getLong("id"),
                nullableLong(rs, "course_id"),
                rs.getString("current_status")
        ), args.toArray()).stream().filter(target -> visible(target.courseId(), visibleCourses)).toList();
    }

    private Optional<StatusTarget> findDeletableTarget(long userId, long notificationId) {
        Set<Long> visibleCourses = Set.copyOf(courseClient.findActiveCourseIds(userId));
        List<StatusTarget> targets = jdbcTemplate.query("""
                SELECT notification.id,notification.course_id,
                       CASE WHEN notification.is_read = TRUE THEN 'READ' ELSE 'UNREAD' END AS current_status
                FROM lrn_notification notification
                """ + visibleNotificationWhere() + """
                  AND notification.id = ?
                """, (rs, rowNum) -> new StatusTarget(
                rs.getLong("id"),
                nullableLong(rs, "course_id"),
                rs.getString("current_status")
        ), userId, notificationId).stream().filter(target -> visible(target.courseId(), visibleCourses)).toList();
        return targets.stream().findFirst();
    }

    private String visibleNotificationWhere() {
        return """
                WHERE notification.user_id = ?
                  AND notification.deleted_at IS NULL
                """;
    }

    private void insertStatusLog(long notificationId, long userId, String oldStatus, String newStatus, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO lrn_notification_status_log
                    (notification_id, user_id, old_status, new_status, operation_type)
                VALUES (?, ?, ?, ?, ?)
                """, notificationId, userId, oldStatus, newStatus, operationType);
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long courseId = nullableLong(rs, "course_id");
        Long sourceId = nullableLong(rs, "source_id");
        return new Notification(
                rs.getLong("id"),
                rs.getLong("user_id"),
                courseId,
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("type"),
                rs.getInt("priority"),
                rs.getBoolean("is_read"),
                rs.getString("source_module"),
                sourceId,
                rs.getString("action_url"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("read_at", LocalDateTime.class)
        );
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private record QueryParts(String whereClause, List<Object> args) {
    }

    private boolean visible(Long courseId, Set<Long> visibleCourses) {
        return courseId == null || visibleCourses.contains(courseId);
    }

    private record StatusTarget(long notificationId, Long courseId, String status) {
    }
}
