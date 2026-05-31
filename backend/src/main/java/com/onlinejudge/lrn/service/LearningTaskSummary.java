package com.onlinejudge.lrn.service;

public record LearningTaskSummary(
        long taskId,
        String taskType,
        String title,
        long courseId,
        String courseName,
        String deadline,
        int progress,
        String status,
        String actionUrl
) {
}
