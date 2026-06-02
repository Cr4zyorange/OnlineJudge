package com.onlinejudge.lrn.service;

public record LearningProgressSaveCommand(
        long courseId,
        Long chapterId,
        String sourceModule,
        long sourceId,
        int progressPercent,
        String lastPosition
) {
}
