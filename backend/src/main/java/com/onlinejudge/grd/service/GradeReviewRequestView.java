package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradeReviewRequest;
import com.onlinejudge.grd.domain.GradeReviewStatus;
import com.onlinejudge.grd.domain.GradeReviewTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeReviewRequestView(
        long requestId,
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
        LocalDateTime processedAt
) {
    public static GradeReviewRequestView from(GradeReviewRequest request) {
        return new GradeReviewRequestView(
                request.id(),
                request.courseId(),
                request.studentId(),
                request.gradeItemId(),
                request.targetType(),
                request.reason(),
                request.status(),
                request.originalScore(),
                request.adjustedScore(),
                request.responseComment(),
                request.submittedAt(),
                request.processedBy(),
                request.processedAt()
        );
    }
}
