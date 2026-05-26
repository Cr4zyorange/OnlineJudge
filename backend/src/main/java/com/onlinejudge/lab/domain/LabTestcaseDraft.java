package com.onlinejudge.lab.domain;

public record LabTestcaseDraft(
        String input,
        String expectedOutput,
        int scoreWeight,
        boolean isPublic,
        int timeLimitMs,
        int memoryLimitKb,
        int orderNum
) {
}
