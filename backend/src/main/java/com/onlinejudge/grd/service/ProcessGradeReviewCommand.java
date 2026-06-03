package com.onlinejudge.grd.service;

import java.math.BigDecimal;

public record ProcessGradeReviewCommand(
        String action,
        BigDecimal adjustedScore,
        String responseComment
) {
}
