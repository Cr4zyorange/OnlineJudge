package com.onlinejudge.grd.domain;

import java.util.List;
import java.util.Optional;

public interface GradePublishRecordRepository {
    GradePublishRecord save(GradePublishRecord record);

    List<GradePublishRecord> findByCourseId(long courseId, int page, int size);

    int countByCourseId(long courseId);

    Optional<GradePublishRecord> findLatestByCourseId(long courseId);

    Optional<GradePublishRecord> findByIdempotencyKey(long courseId, String idempotencyKey);
}
