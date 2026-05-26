package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.GradeRecord;

import java.util.List;

public record CourseGradeRow(
        long studentId,
        CourseGradeSummary summary,
        List<GradeRecord> records
) {
}
