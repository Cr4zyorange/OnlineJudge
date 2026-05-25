package com.onlinejudge.grd.domain;

import java.math.BigDecimal;

public record CreateGradeItemCommand(
        String name,
        SourceType sourceType,
        Long sourceId,
        BigDecimal fullScore,
        BigDecimal weight,
        boolean includedInFinal,
        int sortOrder
) {
}
