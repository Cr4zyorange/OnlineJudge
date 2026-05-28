package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;

public record HomeworkTestCase(
        long id,
        long homeworkId,
        String inputData,
        String expectedOutput,
        BigDecimal scoreWeight,
        boolean hidden,
        int timeLimitMs,
        int memoryLimitKb,
        int sortOrder
) {
    public HomeworkTestCase withHomeworkId(long homeworkId) {
        return new HomeworkTestCase(
                id,
                homeworkId,
                inputData,
                expectedOutput,
                scoreWeight,
                hidden,
                timeLimitMs,
                memoryLimitKb,
                sortOrder
        );
    }
}
