package com.onlinejudge.grd.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GradeAnalysisResult(
        String targetType,
        Long gradeItemId,
        int totalStudentCount,
        int completedCount,
        int missingCount,
        int unsubmittedCount,
        int ungradedCount,
        BigDecimal averageScore,
        BigDecimal maxScore,
        BigDecimal minScore,
        BigDecimal passRate,
        BigDecimal completionRate,
        List<GradeScoreBucket> distribution,
        LocalDateTime sourceDataTime,
        LocalDateTime generatedAt
) {
}
