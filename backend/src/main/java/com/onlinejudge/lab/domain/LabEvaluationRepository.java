package com.onlinejudge.lab.domain;

import java.util.Optional;

public interface LabEvaluationRepository {
    LabEvaluation save(LabEvaluation evaluation);

    LabEvaluation update(LabEvaluation evaluation);

    Optional<LabEvaluation> findLatestBySubmissionId(long submissionId);
}
