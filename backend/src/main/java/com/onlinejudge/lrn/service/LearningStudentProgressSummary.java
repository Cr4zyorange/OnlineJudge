package com.onlinejudge.lrn.service;

public record LearningStudentProgressSummary(
        long studentId,
        String studentName,
        int progressPercent,
        String status,
        String updatedAt
) {
}
