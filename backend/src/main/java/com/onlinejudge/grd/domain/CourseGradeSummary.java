package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseGradeSummary(
        long id,
        long courseId,
        long studentId,
        BigDecimal finalScore,
        FinalStatus finalStatus,
        PublishStatus publishStatus,
        Long calculationBatchId,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public CourseGradeSummary withId(long id) {
        return new CourseGradeSummary(
                id,
                courseId,
                studentId,
                finalScore,
                finalStatus,
                publishStatus,
                calculationBatchId,
                publishedAt,
                createdAt,
                updatedAt
        );
    }

    public CourseGradeSummary adjusted(BigDecimal finalScore, LocalDateTime adjustedAt) {
        return new CourseGradeSummary(
                id,
                courseId,
                studentId,
                finalScore,
                FinalStatus.ADJUSTED,
                publishStatus,
                calculationBatchId,
                publishedAt,
                createdAt,
                adjustedAt
        );
    }

    public CourseGradeSummary published(LocalDateTime publishedAt) {
        return new CourseGradeSummary(
                id,
                courseId,
                studentId,
                finalScore,
                finalStatus,
                PublishStatus.PUBLISHED,
                calculationBatchId,
                publishedAt,
                createdAt,
                publishedAt
        );
    }
}
