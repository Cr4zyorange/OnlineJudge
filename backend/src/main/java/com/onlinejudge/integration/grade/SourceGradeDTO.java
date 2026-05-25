package com.onlinejudge.integration.grade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SourceGradeDTO(
        long courseId,
        SourceGradeType sourceType,
        long sourceId,
        long studentId,
        BigDecimal score,
        BigDecimal fullScore,
        String status,
        LocalDateTime updatedAt
) {
}
