package com.onlinejudge.grd.domain;

import java.time.LocalDateTime;

public record GradeCalculationBatch(
        long id,
        long courseId,
        String triggerType,
        int affectedItemCount,
        int affectedStudentCount,
        String status,
        String message,
        long calculatedBy,
        LocalDateTime calculatedAt
) {
    public GradeCalculationBatch withId(long id) {
        return new GradeCalculationBatch(
                id,
                courseId,
                triggerType,
                affectedItemCount,
                affectedStudentCount,
                status,
                message,
                calculatedBy,
                calculatedAt
        );
    }
}
