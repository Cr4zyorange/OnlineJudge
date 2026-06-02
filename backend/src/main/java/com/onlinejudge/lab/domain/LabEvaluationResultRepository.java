package com.onlinejudge.lab.domain;

import java.util.List;

public interface LabEvaluationResultRepository {
    void replaceSubmissionResults(long submissionId, List<LabEvaluationCaseResult> results);

    List<LabEvaluationCaseResult> findBySubmissionId(long submissionId);
}
