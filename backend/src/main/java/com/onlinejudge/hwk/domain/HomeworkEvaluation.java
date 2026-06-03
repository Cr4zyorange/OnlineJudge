package com.onlinejudge.hwk.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.time.LocalDateTime;

public record HomeworkEvaluation(
        long id,
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkEvaluationType evaluationType,
        EvaluationStatus status,
        int score,
        int passedCases,
        int totalCases,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        String errorMessage,
        String feedback,
        String logUrl,
        String compileLog,
        String runLog,
        boolean reevaluation,
        Long triggeredBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
