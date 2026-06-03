package com.onlinejudge.lrn.service;

import java.util.List;

public record NotificationEventRequest(
        String idempotencyKey,
        String eventType,
        String notificationType,
        Long courseId,
        String sourceModule,
        Long sourceId,
        List<Long> receiverUserIds,
        String title,
        String content,
        Integer priority,
        String actionUrl
) {
}
