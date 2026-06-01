package com.onlinejudge.lrn.domain;

import java.time.LocalDateTime;

public record LearningStudentProgressRow(
        long studentId,
        String studentName,
        long courseId,
        String courseName,
        int progressPercent,
        LocalDateTime updatedAt
) {
}
