package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkStatisticsView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record HomeworkStatisticsResponse(
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
    public static HomeworkStatisticsResponse from(HomeworkStatisticsView view) {
        return new HomeworkStatisticsResponse(
                view.homeworkId(),
                view.courseId(),
                view.totalStudentCount(),
                view.submittedCount(),
                view.unsubmittedCount(),
                view.autoEvaluableCount(),
                view.evaluatedCount(),
                view.pendingEvaluationCount(),
                view.pendingReviewCount(),
                view.reviewedCount(),
                view.scoredCount(),
                view.averageScore(),
                view.maxScore(),
                view.minScore(),
                view.unsubmittedPage(),
                view.unsubmittedSize(),
                view.unsubmittedTotal(),
                view.unsubmittedStudentIds(),
                view.scoreDistribution(),
                view.generatedAt()
        );
    }
}
