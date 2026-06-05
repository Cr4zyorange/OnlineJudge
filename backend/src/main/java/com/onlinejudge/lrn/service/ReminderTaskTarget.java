package com.onlinejudge.lrn.service;

import java.time.LocalDateTime;

public record ReminderTaskTarget(
        long userId,
        long courseId,
        long sourceId,
        String sourceModule,
        String title,
        LocalDateTime deadline,
        String actionUrl
) {
}
