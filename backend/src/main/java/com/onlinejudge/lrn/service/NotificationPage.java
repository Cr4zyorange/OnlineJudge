package com.onlinejudge.lrn.service;

import java.util.List;

public record NotificationPage(
        List<NotificationItem> records,
        long total,
        int page,
        int size,
        long unreadCount
) {
}
