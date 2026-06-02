package com.onlinejudge.lrn.service;

public record LearningTrendPoint(
        String date,
        int durationSeconds,
        int resourceAccessCount,
        int completedTaskCount
) {
}
