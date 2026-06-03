package com.onlinejudge.lrn.service;

public record LearningRecordItem(
        long id,
        long courseId,
        String courseName,
        String sourceModule,
        long sourceId,
        String actionType,
        int durationSeconds,
        String startedAt,
        String endedAt
) {
}
