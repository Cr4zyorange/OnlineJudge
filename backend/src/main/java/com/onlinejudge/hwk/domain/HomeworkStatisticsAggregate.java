package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;

public record HomeworkStatisticsAggregate(
        int submittedCount,
        int autoEvaluableCount,
        int evaluatedCount,
        int pendingEvaluationCount,
        int pendingReviewCount,
        int reviewedCount,
        int scoredCount,
        BigDecimal averageScore,
        BigDecimal maxScore,
        BigDecimal minScore,
        int score0To59,
        int score60To69,
        int score70To79,
        int score80To89,
        int score90To100
) {
    public static HomeworkStatisticsAggregate empty() {
        return new HomeworkStatisticsAggregate(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0
        );
    }
}
