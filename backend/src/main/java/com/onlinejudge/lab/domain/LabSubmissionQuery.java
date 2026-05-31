package com.onlinejudge.lab.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

public record LabSubmissionQuery(
        Long studentId,
        LabSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        Boolean overdue
) {
}
