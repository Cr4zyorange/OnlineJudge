package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;

public record GradeTableQuery(
        String studentKeyword,
        Long gradeItemId,
        GradeStatus gradeStatus,
        PublishStatus publishStatus,
        int page,
        int size
) {
    public GradeTableQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        if (studentKeyword != null && studentKeyword.isBlank()) {
            studentKeyword = null;
        }
    }

    public static GradeTableQuery firstPage() {
        return new GradeTableQuery(null, null, null, null, 1, 20);
    }
}
