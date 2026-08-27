package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HomeworkEvaluationRepository {
    HomeworkEvaluation save(HomeworkEvaluation evaluation);

    HomeworkEvaluation update(HomeworkEvaluation evaluation);

    boolean claimPending(long evaluationId, long submissionId, LocalDateTime startedAt);

    List<HomeworkEvaluation> findPendingCodeEvaluations(int limit);

    int requeueRunningCodeEvaluationsBefore(LocalDateTime startedBefore, LocalDateTime requeuedAt);

    Optional<HomeworkEvaluation> findById(long id);

    Optional<HomeworkEvaluation> findLatestBySubmissionId(long submissionId);
}
