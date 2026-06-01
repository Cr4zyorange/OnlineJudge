package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.ResourceType;
import com.onlinejudge.crs.domain.ResourceVisibility;

import java.time.LocalDateTime;

public record ResourceResponse(
        Long id,
        Long courseId,
        Long chapterId,
        String name,
        ResourceType resourceType,
        ResourceVisibility visibility,
        LocalDateTime publishAt,
        String originalFilename,
        String contentType,
        long fileSize,
        Long uploadUserId,
        String downloadUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
