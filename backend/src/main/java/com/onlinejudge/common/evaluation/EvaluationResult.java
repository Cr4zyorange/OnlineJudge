package com.onlinejudge.common.evaluation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvaluationResult(
        String taskId,
        EvaluationStatus status,
        BigDecimal score,
        String message,
        List<String> caseResults,
        LocalDateTime finishedAt
) {
}
