package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;

public record HomeworkTestCase(
        long id,
        long homeworkId,
        String inputData,
        String expectedOutput,
        int scoreWeight,
        boolean hidden,
        int timeLimitMs,
        int memoryLimitKb,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
                sortOrder,
                createdAt,
                updatedAt
        );
    }
}
