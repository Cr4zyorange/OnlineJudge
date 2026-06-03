package com.onlinejudge.grd.controller;

import com.onlinejudge.grd.domain.GradeReviewTargetType;
import com.onlinejudge.grd.service.SubmitGradeReviewCommand;

public record SubmitGradeReviewRequest(
        Long gradeItemId,
        GradeReviewTargetType targetType,
        String reason
) {
    public SubmitGradeReviewCommand toCommand() {
        return new SubmitGradeReviewCommand(gradeItemId, targetType, reason);
    }
}
