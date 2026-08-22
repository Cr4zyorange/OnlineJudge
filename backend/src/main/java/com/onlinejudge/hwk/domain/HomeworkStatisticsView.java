package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record HomeworkStatisticsView(
        long homeworkId,
        long courseId,
        int totalStudentCount,
        int submittedCount,
        int unsubmittedCount,
        int autoEvaluableCount,
        int evaluatedCount,
        int pendingEvaluationCount,
        int pendingReviewCount,
        int reviewedCount,
        int scoredCount,
        BigDecimal averageScore,
        BigDecimal maxScore,
        BigDecimal minScore,
        int unsubmittedPage,
        int unsubmittedSize,
        int unsubmittedTotal,
        List<Long> unsubmittedStudentIds,
        Map<String, Integer> scoreDistribution,
        LocalDateTime generatedAt
) {
}
