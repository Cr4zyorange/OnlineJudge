package com.onlinejudge.hwk.domain;

import java.util.Optional;

public interface HomeworkEvaluationRepository {
    HomeworkEvaluation save(HomeworkEvaluation evaluation);

    HomeworkEvaluation update(HomeworkEvaluation evaluation);

    Optional<HomeworkEvaluation> findById(long id);

    Optional<HomeworkEvaluation> findLatestBySubmissionId(long submissionId);
}
