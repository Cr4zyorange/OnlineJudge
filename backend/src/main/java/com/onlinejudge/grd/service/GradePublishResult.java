package com.onlinejudge.grd.service;

import java.time.LocalDateTime;

public record GradePublishResult(
        long publishId,
        int publishedCount,
        LocalDateTime publishedAt,
        String notificationStatus
) {
}
