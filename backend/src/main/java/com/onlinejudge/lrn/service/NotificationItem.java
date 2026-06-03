package com.onlinejudge.lrn.service;

public record NotificationItem(
        long notificationId,
        Long courseId,
        String title,
        String content,
        String type,
        int priority,
        boolean isRead,
        String sourceModule,
        Long sourceId,
        String actionUrl,
        String createdAt,
        String readAt
) {
}
