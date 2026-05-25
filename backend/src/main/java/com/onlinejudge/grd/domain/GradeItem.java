package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeItem(
        long id,
        long courseId,
        String name,
        SourceType sourceType,
        Long sourceId,
        BigDecimal fullScore,
        BigDecimal weight,
        boolean includedInFinal,
        boolean enabled,
        int sortOrder,
        long createdBy,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public GradeItem withId(long id) {
        return new GradeItem(
                id,
                courseId,
                name,
                sourceType,
                sourceId,
                fullScore,
                weight,
                includedInFinal,
                enabled,
                sortOrder,
                createdBy,
                deleted,
                createdAt,
                updatedAt
        );
    }

    public GradeItem updateRule(
            String name,
            SourceType sourceType,
            Long sourceId,
            BigDecimal fullScore,
            BigDecimal weight,
            boolean includedInFinal,
            int sortOrder,
            boolean enabled,
            LocalDateTime updatedAt
    ) {
        return new GradeItem(
                id,
                courseId,
                name,
                sourceType,
                sourceId,
                fullScore,
                weight,
                includedInFinal,
                enabled,
                sortOrder,
                createdBy,
                deleted,
                createdAt,
                updatedAt
        );
    }

    public GradeItem disable(LocalDateTime updatedAt) {
        return new GradeItem(
                id,
                courseId,
                name,
                sourceType,
                sourceId,
                fullScore,
                weight,
                includedInFinal,
                false,
                sortOrder,
                createdBy,
                true,
                createdAt,
                updatedAt
        );
    }
}
