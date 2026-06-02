package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabEvaluationResultView;

import java.time.LocalDateTime;
import java.util.List;

public record LabEvaluationResultResponse(
        long submissionId,
        String evaluationStatus,
        int score,
        int passedCases,
        int totalCases,
        String message,
        List<LabEvaluationCaseResultResponse> caseResults,
        LocalDateTime submittedAt,
        LocalDateTime finishedAt
) {
    public static LabEvaluationResultResponse from(LabEvaluationResultView view) {
        return new LabEvaluationResultResponse(
                view.submissionId(),
                view.evaluationStatus().name(),
                view.score(),
                view.passedCases(),
                view.totalCases(),
                view.message(),
                view.caseResults().stream().map(LabEvaluationCaseResultResponse::from).toList(),
                view.submittedAt(),
                view.finishedAt()
        );
    }
}
