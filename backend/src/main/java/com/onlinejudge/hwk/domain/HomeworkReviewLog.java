package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HomeworkReviewLog(
        long id,
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkReviewOperationType operationType,
        BigDecimal oldScore,
        BigDecimal newScore,
        String comment,
        long operatorId,
        String reason,
        LocalDateTime createdAt
) {
}
