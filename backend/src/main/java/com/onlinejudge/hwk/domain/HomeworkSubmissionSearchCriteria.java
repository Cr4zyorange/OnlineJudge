package com.onlinejudge.hwk.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

public record HomeworkSubmissionSearchCriteria(
        String studentKeyword,
        HomeworkSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        HomeworkReviewStatus reviewStatus
) {
    public static HomeworkSubmissionSearchCriteria of(
            String studentKeyword,
            HomeworkSubmitStatus submitStatus,
            EvaluationStatus evaluationStatus,
            HomeworkReviewStatus reviewStatus
    ) {
        return new HomeworkSubmissionSearchCriteria(
                studentKeyword == null || studentKeyword.isBlank() ? null : studentKeyword.trim(),
                submitStatus,
                evaluationStatus,
                reviewStatus
        );
    }
}
