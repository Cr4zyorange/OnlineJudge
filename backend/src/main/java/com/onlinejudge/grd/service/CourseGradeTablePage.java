package com.onlinejudge.grd.service;

import java.util.List;

public record CourseGradeTablePage(
        List<CourseGradeRow> records,
        int total,
        int page,
        int size
) {
}
