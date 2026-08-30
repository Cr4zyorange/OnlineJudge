package com.onlinejudge.integration.grade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SourceGradeDTO(
        long courseId,
        SourceGradeType sourceType,
        long sourceId,
        long studentId,
        BigDecimal score,
        BigDecimal fullScore,
        String status,
        LocalDateTime updatedAt
) {
    /**
     * #310 C-06 来源成绩 DTO 契约版本。字段增加或语义变化必须升级为 v2。
     */
    public static final String VERSION = "v1";
}
