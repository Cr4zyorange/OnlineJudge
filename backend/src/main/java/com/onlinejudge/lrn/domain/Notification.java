package com.onlinejudge.lrn.domain;

import java.time.LocalDateTime;

public record Notification(
        long id,
        long userId,
        Long courseId,
        String title,
        String content,
        String type,
        int priority,
        boolean read,
        String sourceModule,
        Long sourceId,
        String actionUrl,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}
