package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record LabEvaluation(
        long id,
        long submissionId,
        EvaluationStatus status,
        int score,
        int passedCases,
        int totalCases,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        String feedback,
        String compileLog,
        String runLog,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
