package com.onlinejudge.grd.domain;

import java.time.LocalDateTime;

public record GradeAnalysisSourceVersion(
        long version,
        LocalDateTime sourceDataTime
) {
    public static GradeAnalysisSourceVersion initial() {
        return new GradeAnalysisSourceVersion(0L, null);
    }
}
