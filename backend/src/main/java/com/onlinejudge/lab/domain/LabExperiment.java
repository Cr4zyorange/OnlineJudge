package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;
import java.util.List;

public record LabExperiment(
        long id,
        long courseId,
        Long chapterId,
        String title,
        String description,
        LabExperimentStatus status,
        LocalDateTime deadline,
        int maxScore,
        List<Long> attachmentIds,
        String allowedLanguages,
        LabEvaluationMode evaluationMode,
        boolean autoEvaluate,
        boolean reportRequired,
        int timeLimitMs,
        int memoryLimitKb,
        long createdBy,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LabTestcase> testcases
) {
    public LabExperiment withId(long id) {
        return new LabExperiment(
                id,
                courseId,
                chapterId,
                title,
                description,
                status,
                deadline,
                maxScore,
                attachmentIds,
                allowedLanguages,
                evaluationMode,
                autoEvaluate,
                reportRequired,
                timeLimitMs,
                memoryLimitKb,
                createdBy,
                deleted,
                createdAt,
                updatedAt,
                testcases
        );
    }

    public LabExperiment update(
            Long chapterId,
            String title,
            String description,
            LocalDateTime deadline,
            int maxScore,
            List<Long> attachmentIds,
            String allowedLanguages,
            LabEvaluationMode evaluationMode,
            boolean autoEvaluate,
            boolean reportRequired,
            int timeLimitMs,
            int memoryLimitKb,
            LocalDateTime updatedAt,
            List<LabTestcase> testcases
    ) {
        return new LabExperiment(
                id,
                courseId,
                chapterId,
                title,
                description,
                status,
                deadline,
                maxScore,
                attachmentIds,
                allowedLanguages,
                evaluationMode,
                autoEvaluate,
                reportRequired,
                timeLimitMs,
                memoryLimitKb,
                createdBy,
                deleted,
                createdAt,
                updatedAt,
                testcases
        );
    }

    public LabExperiment publish(LocalDateTime updatedAt) {
        return new LabExperiment(
                id,
                courseId,
                chapterId,
                title,
                description,
                LabExperimentStatus.PUBLISHED,
                deadline,
                maxScore,
                attachmentIds,
                allowedLanguages,
                evaluationMode,
                autoEvaluate,
                reportRequired,
                timeLimitMs,
                memoryLimitKb,
                createdBy,
                deleted,
                createdAt,
                updatedAt,
                testcases
        );
    }

    public LabExperiment close(LocalDateTime updatedAt) {
        return new LabExperiment(
                id,
                courseId,
                chapterId,
                title,
                description,
                LabExperimentStatus.CLOSED,
                deadline,
                maxScore,
                attachmentIds,
                allowedLanguages,
                evaluationMode,
                autoEvaluate,
                reportRequired,
                timeLimitMs,
                memoryLimitKb,
                createdBy,
                deleted,
                createdAt,
                updatedAt,
                testcases
        );
    }

    public LabExperiment delete(LocalDateTime updatedAt) {
        return new LabExperiment(
                id,
                courseId,
                chapterId,
                title,
                description,
                status,
                deadline,
                maxScore,
                attachmentIds,
                allowedLanguages,
                evaluationMode,
                autoEvaluate,
                reportRequired,
                timeLimitMs,
                memoryLimitKb,
                createdBy,
                true,
                createdAt,
                updatedAt,
                testcases
        );
    }
}
