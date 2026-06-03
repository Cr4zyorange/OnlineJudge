package com.onlinejudge.lrn.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PersistentNotificationEventPublisher implements NotificationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PersistentNotificationEventPublisher.class);

    private final NotificationService notificationService;

    public PersistentNotificationEventPublisher(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void publish(NotificationEvent event) {
        if (event == null) {
            return;
        }
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
            return;
        }
        try {
            notificationService.createNotifications(new NotificationCreateCommand(
                    event.idempotencyKey(),
                    event.type(),
                    null,
                    event.courseId() > 0 ? event.courseId() : null,
                    normalizeSourceModule(event.targetType()),
                    event.targetId(),
                    event.recipientUserIds(),
                    event.title(),
                    event.content(),
                    1,
                    event.linkUrl()
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to persist notification event targetType={} targetId={}",
                    event.targetType(), event.targetId(), ex);
        }
    }

    private String normalizeSourceModule(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return "SYS";
        }
        String normalized = targetType.trim().toUpperCase();
        if (normalized.startsWith("HOMEWORK") || normalized.startsWith("HWK")) {
            return "HWK";
        }
        if (normalized.startsWith("LAB") || normalized.startsWith("EXPERIMENT")) {
            return "LAB";
        }
        if (normalized.startsWith("GRADE") || normalized.startsWith("GRD")) {
            return "GRD";
        }
        if (normalized.startsWith("COURSE") || normalized.startsWith("CRS") || normalized.startsWith("ANNOUNCEMENT")) {
            return "CRS";
        }
        return "SYS";
    }
}
