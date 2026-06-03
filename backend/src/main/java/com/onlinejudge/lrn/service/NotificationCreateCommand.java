package com.onlinejudge.lrn.service;

import java.util.List;

public record NotificationCreateCommand(
        String idempotencyKey,
        String eventType,
        String notificationType,
        Long courseId,
        String sourceModule,
        Long sourceId,
        List<Long> receiverUserIds,
        String title,
        String content,
        int priority,
        String actionUrl
) {
}
