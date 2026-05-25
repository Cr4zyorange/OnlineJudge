package com.onlinejudge.common.event;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationEvent(
        String idempotencyKey,
        String type,
        long courseId,
        List<Long> recipientUserIds,
        String title,
        String content,
        String targetType,
        Long targetId,
        String linkUrl,
        LocalDateTime occurredAt
) {
}
