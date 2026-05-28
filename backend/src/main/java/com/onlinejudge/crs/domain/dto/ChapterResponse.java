package com.onlinejudge.crs.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChapterResponse(
        Long id,
        Long courseId,
        Long parentId,
        String title,
        String content,
        Integer orderNum,
        List<ChapterResponse> children,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
