package com.onlinejudge.lrn.domain;

import java.time.LocalDateTime;

public record LearningProgress(
        long id,
        long userId,
        long courseId,
        String courseName,
        Long chapterId,
        String chapterName,
        String sourceModule,
        long sourceId,
        int progressPercent,
        String lastPosition,
        String status,
        LocalDateTime updatedAt
) {
}
