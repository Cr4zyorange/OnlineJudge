package com.onlinejudge.lrn.service;

import java.util.List;

public record NotificationEventResult(
        List<Long> notificationIds,
        int createdCount
) {
}
