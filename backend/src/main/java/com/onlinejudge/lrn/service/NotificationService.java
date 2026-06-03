package com.onlinejudge.lrn.service;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.lrn.domain.Notification;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
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

@Service
public class NotificationService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "LEARNING_REMINDER",
            "TASK",
            "GRADE",
            "SYSTEM_ANNOUNCEMENT",
            "TEACHER_ANNOUNCEMENT"
    );
    private static final Set<String> SUPPORTED_MODULES = Set.of("CRS", "LAB", "HWK", "GRD", "SYS");
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final JdbcNotificationRepository notificationRepository;

    public NotificationService(JdbcNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public NotificationPage listNotifications(long userId, NotificationQuery query) {
        String type = normalizeOptionalType(query.type());
        LocalDateTime startTime = parseOptionalTime(query.startTime(), "startTime 不合法");
        LocalDateTime endTime = parseOptionalTime(query.endTime(), "endTime 不合法");
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw badRequest("开始时间不能晚于结束时间");
        }
        int page = normalizePage(query.page());
        int size = normalizeSize(query.size());
        List<NotificationItem> records = notificationRepository.findByUser(
                        userId,
                        type,
                        query.isRead(),
                        startTime,
                        endTime,
                        page,
                        size
                ).stream()
                .map(this::toItem)
                .toList();
        return new NotificationPage(
                records,
                notificationRepository.countByUser(userId, type, query.isRead(), startTime, endTime),
                page,
                size,
                notificationRepository.countUnread(userId)
        );
    }

    @Transactional
    public NotificationEventResult receiveEvent(NotificationEventRequest request) {
        return createNotifications(toCommand(request));
    }

    @Transactional
    public NotificationEventResult createNotifications(NotificationCreateCommand command) {
        NotificationCreateCommand normalized = normalizeCommand(command);
        String type = resolveType(normalized);
        List<Long> createdIds = new ArrayList<>();
        for (Long receiverUserId : normalized.receiverUserIds()) {
            if (receiverUserId == null || receiverUserId <= 0) {
                continue;
            }
            if (normalized.courseId() != null && !notificationRepository.isActiveCourseMember(receiverUserId, normalized.courseId())) {
                continue;
            }
            String idempotencyKey = idempotencyKeyFor(normalized, receiverUserId);
            notificationRepository.save(receiverUserId, type, normalized, idempotencyKey)
                    .ifPresent(createdIds::add);
        }
        return new NotificationEventResult(createdIds, createdIds.size());
    }

    @Transactional
    public NotificationMutationResult markRead(long userId, NotificationReadRequest request) {
        if (request == null) {
            throw badRequest("已读请求不能为空");
        }
        boolean readAll = Boolean.TRUE.equals(request.readAll());
        List<Long> notificationIds = normalizeIds(request.notificationIds());
        if (!readAll && notificationIds.isEmpty()) {
            throw badRequest("通知ID不能为空");
        }
        return new NotificationMutationResult(notificationRepository.markRead(userId, notificationIds, readAll));
    }

    @Transactional
    public NotificationMutationResult deleteNotification(long userId, long notificationId) {
        if (notificationId <= 0) {
            throw badRequest("通知ID不合法");
        }
        int updated = notificationRepository.deleteForUser(userId, notificationId)
                .orElseThrow(() -> new ApiException("LRN-404-04", "通知不存在或无权操作", HttpStatus.NOT_FOUND));
        return new NotificationMutationResult(updated);
    }

    private NotificationCreateCommand toCommand(NotificationEventRequest request) {
        if (request == null) {
            throw badRequest("通知事件不能为空");
        }
        return new NotificationCreateCommand(
                request.idempotencyKey(),
                request.eventType(),
                request.notificationType(),
                request.courseId(),
                request.sourceModule(),
                request.sourceId(),
                request.receiverUserIds(),
                request.title(),
                request.content(),
                request.priority() == null ? 1 : request.priority(),
                request.actionUrl()
        );
    }

    private NotificationCreateCommand normalizeCommand(NotificationCreateCommand command) {
        if (command == null) {
            throw badRequest("通知事件不能为空");
        }
        if (command.receiverUserIds() == null || command.receiverUserIds().isEmpty()) {
            throw badRequest("通知接收人不能为空");
        }
        String sourceModule = normalizeModule(command.sourceModule());
        Long sourceId = command.sourceId();
        if (sourceId != null && sourceId <= 0) {
            throw badRequest("来源对象ID不合法");
        }
        Long courseId = command.courseId();
        if (courseId != null && courseId <= 0) {
            throw badRequest("课程ID不合法");
        }
        String title = requireText(command.title(), "通知标题不能为空");
        String content = requireText(command.content(), "通知内容不能为空");
        int priority = Math.max(1, Math.min(5, command.priority()));
        return new NotificationCreateCommand(
                blankToNull(command.idempotencyKey()),
                blankToNull(command.eventType()),
                blankToNull(command.notificationType()),
                courseId,
                sourceModule,
                sourceId,
                command.receiverUserIds().stream().distinct().toList(),
                title,
                content,
                priority,
                blankToNull(command.actionUrl())
        );
    }

    private String resolveType(NotificationCreateCommand command) {
        String notificationType = normalizeOptionalType(command.notificationType());
        if (notificationType != null) {
            return notificationType;
        }
        String eventType = command.eventType() == null ? "" : command.eventType().toUpperCase(Locale.ROOT);
        if (eventType.contains("REMINDER") || eventType.contains("DEADLINE")) {
            return "LEARNING_REMINDER";
        }
        if (eventType.contains("GRADE") || eventType.contains("SCORE")) {
            return "GRADE";
        }
        if (eventType.contains("ANNOUNCEMENT")) {
            return "CRS".equals(command.sourceModule()) ? "TEACHER_ANNOUNCEMENT" : "SYSTEM_ANNOUNCEMENT";
        }
        if (eventType.contains("HOMEWORK") || eventType.contains("LAB") || eventType.contains("TASK")
                || eventType.contains("PUBLISHED")) {
            return "TASK";
        }
        return "SYSTEM_ANNOUNCEMENT";
    }

    private String idempotencyKeyFor(NotificationCreateCommand command, long receiverUserId) {
        if (command.idempotencyKey() != null) {
            return command.idempotencyKey();
        }
        return String.join(":",
                command.eventType() == null ? "EVENT" : command.eventType(),
                command.sourceModule(),
                command.sourceId() == null ? "0" : String.valueOf(command.sourceId()),
                String.valueOf(receiverUserId)
        );
    }

    private NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.id(),
                notification.courseId(),
                notification.title(),
                notification.content(),
                notification.type(),
                notification.priority(),
                notification.read(),
                notification.sourceModule(),
                notification.sourceId(),
                notification.actionUrl(),
                formatTime(notification.createdAt()),
                formatTime(notification.readAt())
        );
    }

    private String normalizeOptionalType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw badRequest("通知类型不合法");
        }
        return normalized;
    }

    private String normalizeModule(String value) {
        String normalized = requireText(value, "来源模块不能为空").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_MODULES.contains(normalized)) {
            throw badRequest("来源模块不合法");
        }
        return normalized;
    }

    private LocalDateTime parseOptionalTime(String value, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw badRequest(message);
        }
    }

    private int normalizePage(Integer value) {
        return value == null || value < 1 ? DEFAULT_PAGE : value;
    }

    private int normalizeSize(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(value, MAX_SIZE);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private ApiException badRequest(String message) {
        return new ApiException("LRN-400-04", message, HttpStatus.BAD_REQUEST);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }
}
