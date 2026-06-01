package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record LabSubmissionHistoryItemView(
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
}
