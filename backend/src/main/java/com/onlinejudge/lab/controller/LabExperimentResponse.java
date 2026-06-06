package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabExperimentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record LabExperimentResponse(
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
        LocalDateTime publishedAt,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LabTestcaseResponse> testcases
) {
    public static LabExperimentResponse fromTeacherView(LabExperiment experiment) {
        return new LabExperimentResponse(
                experiment.id(),
                experiment.courseId(),
                experiment.chapterId(),
                experiment.title(),
                experiment.description(),
                experiment.status(),
                experiment.deadline(),
                experiment.maxScore(),
                experiment.attachmentIds(),
                experiment.allowedLanguages(),
                experiment.evaluationMode(),
                experiment.autoEvaluate(),
                experiment.reportRequired(),
                experiment.timeLimitMs(),
                experiment.memoryLimitKb(),
                experiment.createdBy(),
                experiment.publishedAt(),
                experiment.deleted(),
                experiment.createdAt(),
                experiment.updatedAt(),
                experiment.testcases().stream()
                        .map(LabTestcaseResponse::fromTeacherView)
                        .toList()
        );
    }

    public static LabExperimentResponse fromStudentView(LabExperiment experiment) {
        return new LabExperimentResponse(
                experiment.id(),
                experiment.courseId(),
                experiment.chapterId(),
                experiment.title(),
                experiment.description(),
                experiment.status(),
                experiment.deadline(),
                experiment.maxScore(),
                experiment.attachmentIds(),
                experiment.allowedLanguages(),
                experiment.evaluationMode(),
                experiment.autoEvaluate(),
                experiment.reportRequired(),
                experiment.timeLimitMs(),
                experiment.memoryLimitKb(),
                experiment.createdBy(),
                experiment.publishedAt(),
                experiment.deleted(),
                experiment.createdAt(),
                experiment.updatedAt(),
                experiment.testcases().stream()
                        .map(LabTestcaseResponse::fromStudentView)
                        .toList()
        );
    }
}
