package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkTestCase;

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
        return new HomeworkTestCaseResponse(
                testCase.id(),
                testCase.homeworkId(),
                testCase.inputData(),
                testCase.expectedOutput(),
                testCase.scoreWeight(),
                testCase.hidden(),
                testCase.timeLimitMs(),
                testCase.memoryLimitKb(),
                testCase.sortOrder()
        );
    }
}
