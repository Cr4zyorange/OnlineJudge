package com.onlinejudge.grd.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeItemCompletionResult(
        long gradeItemId,
        int totalStudentCount,
        int submittedCount,
        int completedCount,
        int missingCount,
        int unsubmittedCount,
        int ungradedCount,
        BigDecimal averageScore,
        BigDecimal completionRate,
        LocalDateTime sourceDataTime,
        LocalDateTime generatedAt
) {
}
