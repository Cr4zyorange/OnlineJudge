package com.onlinejudge.hwk.domain;

import java.util.List;
import java.util.Optional;

public interface HomeworkSubmissionRepository {
    HomeworkSubmission save(HomeworkSubmission submission);

    Optional<HomeworkSubmission> findLatestByHomeworkIdAndStudentId(long homeworkId, long studentId);

    List<HomeworkSubmission> findByHomeworkIdAndStudentId(long homeworkId, long studentId);

    List<HomeworkSubmission> findByHomeworkId(long homeworkId);

    Optional<HomeworkSubmission> findById(long id);

    HomeworkSubmission updateEvaluation(
            long submissionId,
            HomeworkEvaluationStatus evaluationStatus,
            java.math.BigDecimal autoScore,
            java.math.BigDecimal finalScore
    );
}
