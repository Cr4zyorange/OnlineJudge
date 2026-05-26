package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradeChangeLog;

import java.util.List;

public record GradeChangeLogPage(
        List<GradeChangeLog> records,
        int total,
        int page,
        int size
) {
}
