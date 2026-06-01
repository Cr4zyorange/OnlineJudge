package com.onlinejudge.lrn.service;

public record LearningProgressItem(
        long progressId,
        long courseId,
        String courseName,
        Long chapterId,
        String chapterName,
        String sourceModule,
        long sourceId,
        int progressPercent,
        String lastPosition,
        String status,
        String continueUrl,
        String updatedAt
) {
}
