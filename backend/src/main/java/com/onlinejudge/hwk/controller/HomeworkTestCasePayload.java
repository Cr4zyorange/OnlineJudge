package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkTestCase;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record HomeworkTestCasePayload(
        @NotNull String inputData,
        @NotNull String expectedOutput,
        @Min(0) int scoreWeight,
        boolean hidden,
        @Min(1) int timeLimitMs,
        @Min(1) int memoryLimitKb,
        int sortOrder
) {
    HomeworkTestCase toDomain() {
        LocalDateTime now = LocalDateTime.now();
        return new HomeworkTestCase(0L, 0L, inputData, expectedOutput, scoreWeight, hidden, timeLimitMs, memoryLimitKb, sortOrder, now, now);
    }
}
