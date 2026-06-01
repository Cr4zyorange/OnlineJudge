package com.onlinejudge.lrn.service;

public record LearningProgressSaveRequest(
        Long courseId,
        Long chapterId,
        String sourceModule,
        Long sourceId,
        Integer progressPercent,
        String lastPosition
) {
}
