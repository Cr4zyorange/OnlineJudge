package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabEvaluationCaseResult;

public record LabEvaluationCaseResultResponse(
        long testcaseId,
        int orderNum,
        boolean passed,
        int score,
        String input,
        String expectedOutput,
        String actualOutput,
        String message
) {
    public static LabEvaluationCaseResultResponse from(LabEvaluationCaseResult result) {
        return new LabEvaluationCaseResultResponse(
                result.testcaseId(),
                result.orderNum(),
                result.passed(),
                result.score(),
                result.input(),
                result.expectedOutput(),
                result.actualOutput(),
                result.message()
        );
    }
}
