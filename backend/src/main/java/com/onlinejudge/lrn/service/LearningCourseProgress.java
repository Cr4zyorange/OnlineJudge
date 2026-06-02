package com.onlinejudge.lrn.service;

import java.util.List;

public record LearningCourseProgress(
        long courseId,
        String courseName,
        int progressPercent,
        String status,
        String lastPosition,
        String continueUrl,
        String updatedAt,
        LearningProgressItem continueLearning,
        List<LearningChapterProgress> chapters
) {
}
