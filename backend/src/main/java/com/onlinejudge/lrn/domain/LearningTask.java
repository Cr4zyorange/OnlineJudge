package com.onlinejudge.lrn.domain;

import java.time.LocalDateTime;

public record LearningTask(
        long id,
        long userId,
        long courseId,
        String courseName,
        String sourceModule,
        long sourceId,
        String taskType,
        String title,
        LocalDateTime deadline,
        int progress,
        String status,
        String actionUrl,
        LocalDateTime snapshotAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
