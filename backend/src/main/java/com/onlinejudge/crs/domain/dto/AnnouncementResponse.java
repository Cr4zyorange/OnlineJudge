package com.onlinejudge.crs.domain.dto;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        Long courseId,
        String title,
        String content,
        boolean top,
        Long publisherId,
        String publisherName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
