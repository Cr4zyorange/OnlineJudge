package com.onlinejudge.lab.controller;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.lab.domain.LabSubmissionDetailView;
import com.onlinejudge.lab.domain.LabSubmitStatus;

import java.time.LocalDateTime;

public record LabSubmissionDetailResponse(
        long submissionId,
        long labId,
        long studentId,
        String language,
        LabSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        Integer autoScore,
        Integer finalScore,
        int version,
        LocalDateTime submittedAt,
        boolean isLatest,
        boolean isFinal,
        boolean isScoringBasis,
        boolean hasFile,
        String code,
        LabSubmissionSourceFileResponse sourceFile,
        LabReportResponse latestReport,
        LabScoreResponse latestScore
) {
    public static LabSubmissionDetailResponse from(LabSubmissionDetailView detail) {
        return new LabSubmissionDetailResponse(
                detail.submissionId(),
                detail.labId(),
                detail.studentId(),
                detail.language(),
                detail.submitStatus(),
                detail.evaluationStatus(),
                detail.autoScore(),
                detail.finalScore(),
                detail.version(),
                detail.submittedAt(),
                detail.isLatest(),
                detail.isFinal(),
                detail.isScoringBasis(),
                detail.hasFile(),
                detail.code(),
                detail.sourceFile() == null ? null : LabSubmissionSourceFileResponse.from(detail.sourceFile()),
                detail.latestReport() == null ? null : LabReportResponse.from(detail.latestReport()),
                detail.latestScore() == null ? null : LabScoreResponse.from(detail.latestScore())
        );
    }
}
