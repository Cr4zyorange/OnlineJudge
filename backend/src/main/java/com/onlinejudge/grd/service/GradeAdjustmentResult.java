package com.onlinejudge.grd.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeAdjustmentResult(
        long recordId,
        long studentId,
        Long gradeItemId,
        BigDecimal oldScore,
        BigDecimal newScore,
        String reason,
        LocalDateTime updatedAt
) {
}
