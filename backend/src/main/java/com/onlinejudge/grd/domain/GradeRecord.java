package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeRecord(
        long id,
        long courseId,
        long studentId,
        long gradeItemId,
        SourceType sourceType,
        Long sourceId,
        BigDecimal rawScore,
        BigDecimal weightedScore,
        GradeStatus gradeStatus,
        PublishStatus publishStatus,
        String comment,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime calculatedAt,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public GradeRecord withId(long id) {
        return new GradeRecord(
                id,
                courseId,
                studentId,
                gradeItemId,
                sourceType,
                sourceId,
                rawScore,
                weightedScore,
                gradeStatus,
                publishStatus,
                comment,
                sourceUpdatedAt,
                calculatedAt,
                publishedAt,
                createdAt,
                updatedAt
        );
    }

    public GradeRecord adjusted(BigDecimal rawScore, BigDecimal weightedScore, LocalDateTime adjustedAt) {
        return new GradeRecord(
                id,
                courseId,
                studentId,
                gradeItemId,
                sourceType,
                sourceId,
                rawScore,
                weightedScore,
                GradeStatus.ADJUSTED,
                publishStatus,
                comment,
                sourceUpdatedAt,
                adjustedAt,
                publishedAt,
                createdAt,
                adjustedAt
        );
    }
}
