package com.onlinejudge.crs.domain;

import java.time.LocalDateTime;

public record Chapter(
        Long id,
        Long courseId,
        Long parentId,
        String title,
        String content,
        Integer orderNum,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
