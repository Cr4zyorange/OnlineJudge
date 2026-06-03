package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkEvaluationResponse(
        long evaluationId,
        long submissionId,
        EvaluationStatus evaluationStatus,
        int score,
        int passedCases,
        int totalCases,
        Integer durationMs,
        String errorMessage,
        String feedback,
        String compileLog,
        String runLog,
        boolean reevaluation,
        Long triggeredBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    static HomeworkEvaluationResponse from(HomeworkSubmissionService.EvaluationDetail detail) {
        return from(detail.evaluation(), detail.managerView(), detail.homework().type());
    }

    static HomeworkEvaluationResponse from(HomeworkEvaluation evaluation, boolean managerView) {
        return from(evaluation, managerView, null);
    }

    private static HomeworkEvaluationResponse from(
            HomeworkEvaluation evaluation,
            boolean managerView,
            HomeworkType homeworkType
    ) {
        boolean hidePrivateCodeFeedback = !managerView && homeworkType == HomeworkType.CODE;
        return new HomeworkEvaluationResponse(
                evaluation.id(),
                evaluation.submissionId(),
                evaluation.status(),
                evaluation.score(),
                evaluation.passedCases(),
                evaluation.totalCases(),
                evaluation.durationMs(),
                hidePrivateCodeFeedback ? null : evaluation.errorMessage(),
                hidePrivateCodeFeedback ? safeCodeFeedback(evaluation) : evaluation.feedback(),
                managerView ? evaluation.compileLog() : null,
                managerView ? evaluation.runLog() : null,
                evaluation.reevaluation(),
                managerView ? evaluation.triggeredBy() : null,
                evaluation.startedAt(),
                evaluation.finishedAt()
        );
    }

    private static String safeCodeFeedback(HomeworkEvaluation evaluation) {
        return switch (evaluation.status()) {
            case ACCEPTED -> "all cases passed";
            case WRONG_ANSWER -> "passed %d / %d cases".formatted(evaluation.passedCases(), evaluation.totalCases());
            case COMPILE_ERROR -> "compile error";
            case RUNTIME_ERROR -> "runtime error";
            case TIME_LIMIT_EXCEEDED -> "time limit exceeded";
            case SYSTEM_ERROR -> "evaluation failed";
            case RUNNING -> "evaluation running";
            case PENDING -> "waiting for evaluation";
            default -> evaluation.status().name();
        };
    }
}
