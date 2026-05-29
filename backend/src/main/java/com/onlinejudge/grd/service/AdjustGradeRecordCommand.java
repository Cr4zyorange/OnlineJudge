package com.onlinejudge.grd.service;

import java.math.BigDecimal;

public record AdjustGradeRecordCommand(
        BigDecimal newScore,
        String reason
) {
}
