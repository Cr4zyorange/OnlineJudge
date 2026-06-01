package com.onlinejudge.crs.domain;

import java.time.LocalDateTime;

public record CourseResource(
        Long id,
        Long courseId,
        Long chapterId,
        String name,
        ResourceType resourceType,
        ResourceVisibility visibility,
        LocalDateTime publishAt,
        String storageKey,
        String originalFilename,
        String contentType,
        long fileSize,
        Long uploadUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
