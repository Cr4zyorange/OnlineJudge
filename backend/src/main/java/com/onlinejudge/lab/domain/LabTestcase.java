package com.onlinejudge.lab.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record LabTestcase(
        long id,
        long labId,
        String input,
        String expectedOutput,
        int scoreWeight,
        @JsonProperty("public")
        boolean isPublic,
        int timeLimitMs,
        int memoryLimitKb,
        int orderNum,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public LabTestcase withLabId(long labId) {
        return new LabTestcase(
                id,
                labId,
                input,
                expectedOutput,
                scoreWeight,
                isPublic,
                timeLimitMs,
                memoryLimitKb,
                orderNum,
                deleted,
                createdAt,
                updatedAt
        );
    }
}
