package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.util.List;

public record GradeRuleValidationResult(
        boolean valid,
        BigDecimal totalIncludedWeight,
        List<String> errors
) {
}
