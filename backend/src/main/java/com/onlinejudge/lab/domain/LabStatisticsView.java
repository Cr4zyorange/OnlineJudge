package com.onlinejudge.lab.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LabStatisticsView(
        long labId,
        long courseId,
        int totalStudentCount,
        int submittedCount,
        int unsubmittedCount,
        int evaluatedCount,
        BigDecimal submissionRate,
        BigDecimal evaluationCompletionRate,
        BigDecimal averageScore,
        int lateSubmissionCount,
        List<Long> unsubmittedStudentIds,
        Map<String, Integer> scoreDistribution,
        LocalDateTime generatedAt
) {
}
