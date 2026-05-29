package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.GradePublishRecord;

import java.util.List;

public record GradePublishRecordPage(
        List<GradePublishRecord> records,
        int total,
        int page,
        int size
) {
}
