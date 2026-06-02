package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record LabEvaluationResultView(
        long submissionId,
        EvaluationStatus evaluationStatus,
        int score,
        int passedCases,
        int totalCases,
        String message,
        List<LabEvaluationCaseResult> caseResults,
        LocalDateTime submittedAt,
        LocalDateTime finishedAt
) {
}
