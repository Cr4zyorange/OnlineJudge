package com.onlinejudge.grd.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinalScoreAdjustmentResult(
        long summaryId,
        long studentId,
        BigDecimal oldScore,
        BigDecimal newScore,
        String reason,
        LocalDateTime updatedAt
) {
}
