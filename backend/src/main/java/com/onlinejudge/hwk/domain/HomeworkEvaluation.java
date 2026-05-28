package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HomeworkEvaluation(
        long id,
        long homeworkId,
        long submissionId,
        String evaluatorType,
        HomeworkEvaluationStatus status,
        BigDecimal score,
        BigDecimal totalScore,
        int passedCount,
        int totalCount,
        String caseResultsJson,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
