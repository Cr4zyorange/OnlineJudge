package com.onlinejudge.grd.service;

public record GradeRecalculationResult(
        long calculationBatchId,
        int affectedCount
) {
}
