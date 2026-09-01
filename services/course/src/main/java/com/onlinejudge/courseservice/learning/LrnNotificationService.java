package com.onlinejudge.courseservice.learning;

import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Course-owned notification facts (LRN folded into Course, #355). */
@Service
public class LrnNotificationService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "LEARNING_REMINDER", "TASK", "GRADE", "SYSTEM_ANNOUNCEMENT", "TEACHER_ANNOUNCEMENT");
    private static final Set<String> SUPPORTED_MODULES = Set.of("CRS", "LAB", "HWK", "GRD", "SYS");
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final LrnNotificationRepository notifications;

    public LrnNotificationService(LrnNotificationRepository notifications) { this.notifications = notifications; }

    public NotificationPage list(long userId, NotificationQuery query) {
        String type = normalizeOptionalType(query.type());
        LocalDateTime startTime = parseOptionalTime(query.startTime());
        LocalDateTime endTime = parseOptionalTime(query.endTime());
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw badRequest("开始时间不能晚于结束时间");
        }
        int page = query.page() == null || query.page() < 1 ? DEFAULT_PAGE : query.page();
        int size = query.size() == null || query.size() < 1 ? DEFAULT_SIZE : Math.min(query.size(), MAX_SIZE);
        List<NotificationItem> records = notifications.findByUser(userId, type, query.isRead(), startTime, endTime, page, size)
                .stream().map(this::toItem).toList();
        return new NotificationPage(records, notifications.countByUser(userId, type, query.isRead(), startTime, endTime),
                page, size, notifications.countUnread(userId));
    }

    @Transactional
    public NotificationEventResult receiveEvent(NotificationEventRequest request) {
        if (request == null) throw badRequest("通知事件不能为空");
        return create(request.idempotencyKey(), request.eventType(), request.notificationType(), request.courseId(),
                request.sourceModule(), request.sourceId(), request.receiverUserIds(), request.title(), request.content(),
                request.priority(), request.actionUrl());
    }

    /** Idempotent fact projection: the eventId is part of the per-receiver idempotency key. */
    @Transactional
    public NotificationEventResult createForFact(String eventId, String eventType, String notificationType, Long courseId,
                                                 String sourceModule, Long sourceId, List<Long> receiverUserIds,
                                                 String title, String content, Integer priority, String actionUrl) {
        return create(eventId, eventType, notificationType, courseId, sourceModule, sourceId, receiverUserIds,
                title, content, priority, actionUrl);
    }

    @Transactional
    public NotificationMutationResult markRead(long userId, NotificationReadRequest request) {
        if (request == null) throw badRequest("已读请求不能为空");
        boolean readAll = Boolean.TRUE.equals(request.readAll());
        List<Long> ids = request.notificationIds() == null ? List.of()
                : request.notificationIds().stream().filter(id -> id != null && id > 0).distinct().toList();
        if (!readAll && ids.isEmpty()) throw badRequest("通知ID不能为空");
        return new NotificationMutationResult(notifications.markRead(userId, ids, readAll));
    }

    @Transactional
    public NotificationMutationResult delete(long userId, long notificationId) {
        if (notificationId <= 0) throw badRequest("通知ID不合法");
        int updated = notifications.deleteForUser(userId, notificationId);
        if (updated == 0) {
            throw new CourseException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在或无权操作", false);
        }
        return new NotificationMutationResult(updated);
    }

    private NotificationEventResult create(String idempotencyKey, String eventType, String notificationType, Long courseId,
                                           String sourceModule, Long sourceId, List<Long> receiverUserIds,
                                           String title, String content, Integer priority, String actionUrl) {
        if (receiverUserIds == null || receiverUserIds.isEmpty()) throw badRequest("通知接收人不能为空");
        String module = normalizeModule(sourceModule);
        if (sourceId != null && sourceId <= 0) throw badRequest("来源对象ID不合法");
        if (courseId != null && courseId <= 0) throw badRequest("课程ID不合法");
        String normalizedTitle = requireText(title, "通知标题不能为空");
        String normalizedContent = requireText(content, "通知内容不能为空");
        int normalizedPriority = priority == null ? 1 : Math.max(1, Math.min(5, priority));
        String type = resolveType(notificationType, eventType, module);
        String idempotency = idempotencyKey == null || idempotencyKey.isBlank()
                ? String.join(":", eventType == null ? "EVENT" : eventType, module,
                        sourceId == null ? "0" : String.valueOf(sourceId))
                : idempotencyKey.trim();
        List<Long> createdIds = new ArrayList<>();
        for (Long receiverUserId : receiverUserIds.stream().distinct().toList()) {
            if (receiverUserId == null || receiverUserId <= 0) continue;
            if (courseId != null && !notifications.isActiveProjectedMember(receiverUserId, courseId)) continue;
            notifications.save(receiverUserId, courseId, idempotency + ":" + receiverUserId, normalizedTitle, normalizedContent,
                            type, normalizedPriority, module, sourceId, blankToNull(actionUrl))
                    .ifPresent(createdIds::add);
        }
        return new NotificationEventResult(createdIds, createdIds.size());
    }

    private String resolveType(String notificationType, String eventType, String module) {
        String explicit = normalizeOptionalType(notificationType);
        if (explicit != null) return explicit;
        String event = eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
        if (event.contains("REMINDER") || event.contains("DEADLINE")) return "LEARNING_REMINDER";
        if (event.contains("GRADE") || event.contains("SCORE")) return "GRADE";
        if (event.contains("ANNOUNCEMENT")) return "CRS".equals(module) ? "TEACHER_ANNOUNCEMENT" : "SYSTEM_ANNOUNCEMENT";
        return "TASK";
    }

    private NotificationItem toItem(LrnNotificationRepository.NotificationRow row) {
        return new NotificationItem(row.id(), row.courseId(), row.title(), row.content(), row.type(), row.priority(),
                row.read(), row.sourceModule(), row.sourceId(), row.actionUrl(),
                format(row.createdAt()), format(row.readAt()));
    }

    private String normalizeOptionalType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) throw badRequest("通知类型不合法");
        return normalized;
    }

    private String normalizeModule(String value) {
        String normalized = requireText(value, "来源模块不能为空").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_MODULES.contains(normalized)) throw badRequest("来源模块不合法");
        return normalized;
    }

    private LocalDateTime parseOptionalTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw badRequest("时间格式不合法");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CourseException badRequest(String message) {
        return new CourseException(HttpStatus.BAD_REQUEST, "NOTIFICATION_INVALID", message, false);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }

    public record NotificationPage(List<NotificationItem> records, long total, int page, int size, long unreadCount) { }
    public record NotificationItem(long notificationId, Long courseId, String title, String content, String type, int priority,
                                   boolean isRead, String sourceModule, Long sourceId, String actionUrl,
                                   String createdAt, String readAt) { }
    public record NotificationQuery(String type, Boolean isRead, String startTime, String endTime, Integer page, Integer size) { }
    public record NotificationReadRequest(List<Long> notificationIds, Boolean readAll) { }
    public record NotificationEventRequest(String idempotencyKey, String eventType, String notificationType, Long courseId,
                                           String sourceModule, Long sourceId, List<Long> receiverUserIds,
                                           String title, String content, Integer priority, String actionUrl) { }
    public record NotificationEventResult(List<Long> notificationIds, int createdCount) { }
    public record NotificationMutationResult(int updatedCount) { }
}
