package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabResultView(
        long labId,
        long studentId,
        LabExperimentStatus status,
        LabSubmissionDetailView submission,
        LabEvaluationResultView evaluationResult,
        LabReportSummaryView latestReport,
        LabScoreSummaryView latestScore,
        LocalDateTime publishedAt
) {
}
