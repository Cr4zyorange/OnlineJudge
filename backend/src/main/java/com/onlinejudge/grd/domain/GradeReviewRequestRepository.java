package com.onlinejudge.grd.domain;

import java.util.List;
import java.util.Optional;

public interface GradeReviewRequestRepository {
    GradeReviewRequest save(GradeReviewRequest request);

    GradeReviewRequest update(GradeReviewRequest request);

    Optional<GradeReviewRequest> findById(long id);

    Optional<GradeReviewRequest> findPendingByTarget(
            long courseId,
            long studentId,
            GradeReviewTargetType targetType,
            Long gradeItemId
    );

    List<GradeReviewRequest> findByCourseId(
            long courseId,
            Long studentId,
            Long gradeItemId,
            GradeReviewStatus status,
            int page,
            int size
    );

    int countByCourseId(long courseId, Long studentId, Long gradeItemId, GradeReviewStatus status);
}
