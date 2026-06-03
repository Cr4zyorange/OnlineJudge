package com.onlinejudge.lrn.service;

import java.util.List;

public record NotificationReadRequest(
        List<Long> notificationIds,
        Boolean readAll
) {
}
