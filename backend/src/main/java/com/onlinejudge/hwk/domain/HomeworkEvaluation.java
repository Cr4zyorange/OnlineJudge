package com.onlinejudge.hwk.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record HomeworkEvaluation(
        long id,
        long submissionId,
        EvaluationStatus status,
        int score,
        int passedCases,
        int totalCases,
        Integer durationMs,
        String errorMessage,
        String feedback,
        String compileLog,
        String runLog,
        boolean reevaluation,
        Long triggeredBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
