package com.onlinejudge.grd.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeChangeLog(
        long id,
        long courseId,
        long studentId,
        Long gradeItemId,
        String changeType,
        BigDecimal oldValue,
        BigDecimal newValue,
        String reason,
        long operatorId,
        LocalDateTime createdAt
) {
    public GradeChangeLog withId(long id) {
        return new GradeChangeLog(
                id,
                courseId,
                studentId,
                gradeItemId,
                changeType,
                oldValue,
                newValue,
                reason,
                operatorId,
                createdAt
        );
    }
}
