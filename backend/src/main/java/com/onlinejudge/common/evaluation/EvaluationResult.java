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
    /**
     * #310 C-07 评测结果契约版本。
     */
    public static final String VERSION = "v1";
}
