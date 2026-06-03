package com.onlinejudge.grd.controller;

import com.onlinejudge.grd.service.ProcessGradeReviewCommand;

import java.math.BigDecimal;

public record ProcessGradeReviewRequest(
        String action,
        BigDecimal adjustedScore,
        String responseComment
) {
    public ProcessGradeReviewCommand toCommand() {
        return new ProcessGradeReviewCommand(action, adjustedScore, responseComment);
    }
}
