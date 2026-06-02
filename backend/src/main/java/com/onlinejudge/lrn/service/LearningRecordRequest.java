package com.onlinejudge.lrn.service;

import java.time.LocalDateTime;

public record LearningRecordRequest(
        Long courseId,
        String sourceModule,
        Long sourceId,
        String actionType,
        Integer durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
