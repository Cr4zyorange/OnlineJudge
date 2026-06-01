package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record LabEvaluationCaseResult(
        long id,
        long submissionId,
        long testcaseId,
        int orderNum,
        EvaluationStatus status,
        boolean passed,
        int score,
        String input,
        String expectedOutput,
        String actualOutput,
        String message,
        LocalDateTime executedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
