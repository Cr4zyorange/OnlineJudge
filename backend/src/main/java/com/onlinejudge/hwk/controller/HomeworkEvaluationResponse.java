package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HomeworkEvaluationResponse(
        long id,
        long homeworkId,
        long submissionId,
        String evaluatorType,
        HomeworkEvaluationStatus status,
        BigDecimal score,
        BigDecimal totalScore,
        int passedCount,
        int totalCount,
        String caseResultsJson,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    static HomeworkEvaluationResponse from(HomeworkEvaluation evaluation) {
        return new HomeworkEvaluationResponse(
                evaluation.id(),
                evaluation.homeworkId(),
                evaluation.submissionId(),
                evaluation.evaluatorType(),
                evaluation.status(),
                evaluation.score(),
                evaluation.totalScore(),
                evaluation.passedCount(),
                evaluation.totalCount(),
                evaluation.caseResultsJson(),
                evaluation.message(),
                evaluation.startedAt(),
                evaluation.finishedAt()
        );
    }
}
