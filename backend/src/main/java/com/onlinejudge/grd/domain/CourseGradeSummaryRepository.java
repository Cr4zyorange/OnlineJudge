package com.onlinejudge.grd.domain;

import java.util.List;

public interface CourseGradeSummaryRepository {
    CourseGradeSummary upsert(CourseGradeSummary summary);

    List<CourseGradeSummary> findByCourseId(long courseId);
}
