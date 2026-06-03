package com.onlinejudge.lrn.domain;

import java.time.LocalDateTime;

public record LearningRecord(
        long id,
        long userId,
        long courseId,
        String courseName,
        String sourceModule,
        long sourceId,
        String actionType,
        int durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
}
