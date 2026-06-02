package com.onlinejudge.crs.domain;

import java.time.LocalDateTime;

public record Announcement(
        Long id,
        Long courseId,
        String title,
        String content,
        boolean top,
        Long publisherId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
