package com.onlinejudge.grd.domain;

import java.util.List;

public interface GradeChangeLogRepository {
    GradeChangeLog save(GradeChangeLog log);

    List<GradeChangeLog> findByCourseId(long courseId, Long studentId, Long gradeItemId, int page, int size);

    int countByCourseId(long courseId, Long studentId, Long gradeItemId);
}
