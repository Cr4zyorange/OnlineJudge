package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeReviewRequest(
        long id,
        long courseId,
        long studentId,
        Long gradeItemId,
        GradeReviewTargetType targetType,
        String reason,
        GradeReviewStatus status,
        BigDecimal originalScore,
        BigDecimal adjustedScore,
        String responseComment,
        LocalDateTime submittedAt,
        Long processedBy,
        LocalDateTime processedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public GradeReviewRequest withId(long id) {
        return new GradeReviewRequest(
                id,
                courseId,
                studentId,
                gradeItemId,
                targetType,
                reason,
                status,
                originalScore,
                adjustedScore,
                responseComment,
                submittedAt,
                processedBy,
                processedAt,
                createdAt,
                updatedAt
        );
    }

    public GradeReviewRequest processed(
            GradeReviewStatus status,
            BigDecimal adjustedScore,
            String responseComment,
            long processedBy,
            LocalDateTime processedAt
    ) {
        return new GradeReviewRequest(
                id,
                courseId,
                studentId,
                gradeItemId,
                targetType,
                reason,
                status,
                originalScore,
                adjustedScore,
                responseComment,
                submittedAt,
                processedBy,
                processedAt,
                createdAt,
                processedAt
        );
    }
}
