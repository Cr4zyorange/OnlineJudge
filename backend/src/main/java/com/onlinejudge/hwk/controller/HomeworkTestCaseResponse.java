package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.hwk.domain.HomeworkTestCase;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkTestCaseResponse(
        long id,
        long homeworkId,
        String inputData,
        String expectedOutput,
        int scoreWeight,
        boolean hidden,
        int timeLimitMs,
        int memoryLimitKb,
        int sortOrder
) {
    static HomeworkTestCaseResponse from(HomeworkTestCase testCase) {
        return from(testCase, true);
    }

    static HomeworkTestCaseResponse fromStudentView(HomeworkTestCase testCase) {
        return from(testCase, false);
    }

    private static HomeworkTestCaseResponse from(HomeworkTestCase testCase, boolean includeHiddenOutput) {
        return new HomeworkTestCaseResponse(
                testCase.id(),
                testCase.homeworkId(),
                testCase.inputData(),
                includeHiddenOutput || !testCase.hidden() ? testCase.expectedOutput() : null,
                testCase.scoreWeight(),
                testCase.hidden(),
                testCase.timeLimitMs(),
                testCase.memoryLimitKb(),
                testCase.sortOrder()
        );
    }
}
