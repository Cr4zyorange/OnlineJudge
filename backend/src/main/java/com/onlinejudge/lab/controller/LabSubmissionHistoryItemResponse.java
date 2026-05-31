package com.onlinejudge.lab.controller;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.lab.domain.LabSubmissionHistoryItemView;
import com.onlinejudge.lab.domain.LabSubmitStatus;

import java.time.LocalDateTime;

public record LabSubmissionHistoryItemResponse(
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
        boolean hasFile
) {
    public static LabSubmissionHistoryItemResponse from(LabSubmissionHistoryItemView item) {
        return new LabSubmissionHistoryItemResponse(
                item.submissionId(),
                item.labId(),
                item.studentId(),
                item.language(),
                item.submitStatus(),
                item.evaluationStatus(),
                item.autoScore(),
                item.finalScore(),
                item.version(),
                item.submittedAt(),
                item.isLatest(),
                item.isFinal(),
                item.isScoringBasis(),
                item.hasFile()
        );
    }
}
