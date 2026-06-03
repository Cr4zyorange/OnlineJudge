package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradeReviewTargetType;

public record SubmitGradeReviewCommand(
        Long gradeItemId,
        GradeReviewTargetType targetType,
        String reason
) {
}
