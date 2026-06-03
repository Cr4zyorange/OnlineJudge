package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradeReviewStatus;

import java.time.LocalDateTime;

public record GradeReviewSubmissionResult(
        long requestId,
        GradeReviewStatus status,
        LocalDateTime submittedAt
) {
}
