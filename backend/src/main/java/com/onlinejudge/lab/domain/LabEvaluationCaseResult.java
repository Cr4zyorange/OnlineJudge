package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record LabEvaluationCaseResult(
        long id,
        long submissionId,
        long testcaseId,
        int orderNum,
        boolean isPublic,
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
    public LabEvaluationCaseResult hideSensitiveContent() {
        return new LabEvaluationCaseResult(
                id,
                submissionId,
                testcaseId,
                orderNum,
                isPublic,
                status,
                passed,
                score,
                isPublic ? input : null,
                isPublic ? expectedOutput : null,
                actualOutput,
                message,
                executedAt,
                createdAt,
                updatedAt
        );
    }
}
