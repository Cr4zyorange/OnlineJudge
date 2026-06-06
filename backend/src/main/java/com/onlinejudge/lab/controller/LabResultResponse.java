package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabResultView;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LabResultResponse(
        long labId,
        long studentId,
        LabExperimentStatus status,
        LabSubmissionDetailResponse submission,
        LabEvaluationResultResponse evaluationResult,
        LabReportResponse latestReport,
        LabScoreResponse latestScore,
        LocalDateTime publishedAt
) {
    public static LabResultResponse from(LabResultView view) {
        return new LabResultResponse(
                view.labId(),
                view.studentId(),
                view.status(),
                LabSubmissionDetailResponse.from(view.submission()),
                LabEvaluationResultResponse.from(view.evaluationResult()),
                view.latestReport() == null ? null : LabReportResponse.from(view.latestReport()),
                view.latestScore() == null ? null : LabScoreResponse.from(view.latestScore()),
                view.publishedAt()
        );
    }
}
