package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;

public record HomeworkJudgeConfig(
        long id,
        long homeworkId,
        String languageLimitJson,
        int timeLimitMs,
        int memoryLimitKb,
        String outputCompareMode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public HomeworkJudgeConfig withHomeworkId(long homeworkId) {
        return new HomeworkJudgeConfig(
                id,
                homeworkId,
                languageLimitJson,
                timeLimitMs,
                memoryLimitKb,
                outputCompareMode,
                createdAt,
                updatedAt
        );
    }
}
