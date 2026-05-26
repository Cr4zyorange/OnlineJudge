package com.onlinejudge.grd.controller;

import java.math.BigDecimal;

public record AdjustGradeRecordRequest(
        BigDecimal newScore,
        String reason
) {
}
