package com.onlinejudge.hwk.domain;

import java.util.List;
import java.util.Optional;

public interface HomeworkSubmissionRepository {
    HomeworkSubmission save(HomeworkSubmission submission);

    void clearFinalSubmission(long homeworkId, long studentId);

    Optional<HomeworkSubmission> findFinalByHomeworkAndStudent(long homeworkId, long studentId);

    List<HomeworkSubmission> findByHomeworkAndStudent(long homeworkId, long studentId);

    Optional<HomeworkSubmission> findById(long id);

    List<HomeworkSubmission> findByHomework(
            long homeworkId,
            Long studentId,
            String submitStatus,
            String evaluationStatus,
            String reviewStatus,
            int page,
            int size
    );

    long countByHomework(
            long homeworkId,
            Long studentId,
            String submitStatus,
            String evaluationStatus,
            String reviewStatus
    );
}
