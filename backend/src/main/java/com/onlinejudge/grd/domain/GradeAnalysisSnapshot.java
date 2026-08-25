package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeAnalysisSnapshot(
        long id,
        long courseId,
        String targetType,
        Long gradeItemId,
        LocalDateTime sourceDataTime,
        String sourceFingerprint,
        BigDecimal averageScore,
        BigDecimal maxScore,
        BigDecimal minScore,
        BigDecimal passRate,
        BigDecimal completionRate,
        String distributionJson,
        long generatedBy,
        LocalDateTime generatedAt
) {
    public GradeAnalysisSnapshot(
            long id,
            long courseId,
            String targetType,
            Long gradeItemId,
            LocalDateTime sourceDataTime,
            BigDecimal averageScore,
            BigDecimal maxScore,
            BigDecimal minScore,
            BigDecimal passRate,
            BigDecimal completionRate,
            String distributionJson,
            long generatedBy,
            LocalDateTime generatedAt
    ) {
        this(
                id,
                courseId,
                targetType,
                gradeItemId,
                sourceDataTime,
                null,
                averageScore,
                maxScore,
                minScore,
                passRate,
                completionRate,
                distributionJson,
                generatedBy,
                generatedAt
        );
    }

    public GradeAnalysisSnapshot withId(long id) {
        return new GradeAnalysisSnapshot(
                id,
                courseId,
                targetType,
                gradeItemId,
                sourceDataTime,
                sourceFingerprint,
                averageScore,
                maxScore,
                minScore,
                passRate,
                completionRate,
                distributionJson,
                generatedBy,
                generatedAt
        );
    }
}
