package com.onlinejudge.crs.domain;

import java.time.LocalDateTime;

public record Chapter(
        Long id,
        Long courseId,
        Long parentId,
        String chapterName,
        Integer sortOrder,
        String objective,
        Integer visibleStatus,
        Integer chapterType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
