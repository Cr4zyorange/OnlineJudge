package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradeReviewStatus;

import java.time.LocalDateTime;

public record GradeReviewProcessResult(
        long requestId,
        GradeReviewStatus status,
        LocalDateTime processedAt
) {
}
