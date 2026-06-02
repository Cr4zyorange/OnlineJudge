package com.onlinejudge.lrn.service;

public record LearningStatisticsSummary(
        int totalDurationSeconds,
        int resourceAccessCount,
        int completedTaskCount,
        int submittedTaskCount,
        int totalRecordCount
) {
}
