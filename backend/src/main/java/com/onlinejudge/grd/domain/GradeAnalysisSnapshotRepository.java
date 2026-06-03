package com.onlinejudge.grd.domain;

import java.util.Optional;

public interface GradeAnalysisSnapshotRepository {
    GradeAnalysisSnapshot save(GradeAnalysisSnapshot snapshot);

    Optional<GradeAnalysisSnapshot> findLatest(long courseId, String targetType, Long gradeItemId);
}
