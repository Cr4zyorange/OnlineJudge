package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record LabSubmission(
        long id,
        long labId,
        long studentId,
        String codeContent,
        String fileId,
        String language,
        LabSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        Integer finalScore,
        Integer autoScore,
        int version,
        boolean isFinal,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted
) {
    public LabSubmission withId(long id) {
        return new LabSubmission(
                id,
                labId,
                studentId,
                codeContent,
                fileId,
                language,
                submitStatus,
                evaluationStatus,
                finalScore,
                autoScore,
                version,
                isFinal,
                submittedAt,
                createdAt,
                updatedAt,
                deleted
        );
    }

    public LabSubmission markHistorical(LocalDateTime updatedAt) {
        return new LabSubmission(
                id,
                labId,
                studentId,
                codeContent,
                fileId,
                language,
                submitStatus,
                evaluationStatus,
                finalScore,
                autoScore,
                version,
                false,
                submittedAt,
                createdAt,
                updatedAt,
                deleted
        );
    }
}
