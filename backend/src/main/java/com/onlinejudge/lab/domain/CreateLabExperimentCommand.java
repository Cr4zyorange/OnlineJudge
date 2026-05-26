package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;
import java.util.List;

public record CreateLabExperimentCommand(
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
        List<LabTestcaseDraft> testcases
) {
}
