package com.onlinejudge.lrn.service;

import java.time.LocalDateTime;

public record LearningRecordCommand(
        long courseId,
        String sourceModule,
        long sourceId,
        String actionType,
        int durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
