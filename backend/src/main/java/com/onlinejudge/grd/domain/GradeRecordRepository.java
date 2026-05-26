package com.onlinejudge.grd.domain;

import java.util.List;

public interface GradeRecordRepository {
    GradeRecord upsert(GradeRecord record);

    List<GradeRecord> findByCourseId(long courseId);
}
