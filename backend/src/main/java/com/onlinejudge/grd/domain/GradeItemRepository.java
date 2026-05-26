package com.onlinejudge.grd.domain;

import java.util.List;
import java.util.Optional;

public interface GradeItemRepository {
    GradeItem save(GradeItem item);

    GradeItem update(GradeItem item);

    Optional<GradeItem> findById(long id);

    List<GradeItem> findByCourseId(long courseId);
}
