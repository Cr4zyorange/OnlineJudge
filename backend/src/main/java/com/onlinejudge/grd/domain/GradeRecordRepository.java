package com.onlinejudge.grd.domain;

import java.util.List;
import java.util.Optional;

public interface GradeRecordRepository {
    GradeRecord upsert(GradeRecord record);

    GradeRecord update(GradeRecord record);

    Optional<GradeRecord> findById(long id);

    List<GradeRecord> findByCourseId(long courseId);
}
