package com.onlinejudge.lrn.service;

import java.util.List;

public record LearningChapterProgress(
        long chapterId,
        String chapterName,
        int progressPercent,
        String status,
        String lastPosition,
        String continueUrl,
        String updatedAt,
        List<LearningProgressItem> records
) {
}
