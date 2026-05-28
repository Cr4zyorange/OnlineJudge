package com.onlinejudge.hwk.domain;

import java.util.Optional;

public interface HomeworkEvaluationRepository {
    HomeworkEvaluation save(HomeworkEvaluation evaluation);

    Optional<HomeworkEvaluation> findLatestBySubmissionId(long submissionId);
}
