package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.hwk.domain.HomeworkTestCase;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkTestCaseResponse(
        long id,
        String inputData,
        String expectedOutput,
        BigDecimal scoreWeight,
        boolean hidden,
        int timeLimitMs,
        int memoryLimitKb,
        int sortOrder
) {
    static HomeworkTestCaseResponse from(HomeworkTestCase testCase, boolean includeHiddenContent) {
        return new HomeworkTestCaseResponse(
                testCase.id(),
                includeHiddenContent || !testCase.hidden() ? testCase.inputData() : null,
                includeHiddenContent || !testCase.hidden() ? testCase.expectedOutput() : null,
                testCase.scoreWeight(),
                testCase.hidden(),
                testCase.timeLimitMs(),
                testCase.memoryLimitKb(),
                testCase.sortOrder()
        );
    }
}
