package com.onlinejudge.grd.domain;

import java.util.List;
import java.util.Optional;

public interface CourseGradeSummaryRepository {
    CourseGradeSummary upsert(CourseGradeSummary summary);

    CourseGradeSummary update(CourseGradeSummary summary);

    Optional<CourseGradeSummary> findById(long id);

    List<CourseGradeSummary> findByCourseId(long courseId);
}
