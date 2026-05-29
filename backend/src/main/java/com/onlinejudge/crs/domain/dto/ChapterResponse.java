package com.onlinejudge.crs.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChapterResponse(
        Long id,
        Long courseId,
        Long parentId,
        String chapterName,
        Integer sortOrder,
        String objective,
        Integer visibleStatus,
        Integer chapterType,
        List<ChapterResponse> children,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
