package com.onlinejudge.hwk.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.util.List;

public record HomeworkSubmissionSearchCriteria(
        String studentKeyword,
        HomeworkSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        HomeworkReviewStatus reviewStatus,
        HomeworkSubmissionAttention attention,
        List<Long> activeStudentIds
) {
    public HomeworkSubmissionSearchCriteria {
        activeStudentIds = activeStudentIds == null ? List.of() : List.copyOf(activeStudentIds);
    }

    public static HomeworkSubmissionSearchCriteria of(
            String studentKeyword,
            HomeworkSubmitStatus submitStatus,
            EvaluationStatus evaluationStatus,
            HomeworkReviewStatus reviewStatus
    ) {
        return of(studentKeyword, submitStatus, evaluationStatus, reviewStatus, null);
    }

    public static HomeworkSubmissionSearchCriteria of(
            String studentKeyword,
            HomeworkSubmitStatus submitStatus,
            EvaluationStatus evaluationStatus,
            HomeworkReviewStatus reviewStatus,
            HomeworkSubmissionAttention attention
    ) {
        return new HomeworkSubmissionSearchCriteria(
                studentKeyword == null || studentKeyword.isBlank() ? null : studentKeyword.trim(),
                submitStatus,
                evaluationStatus,
                reviewStatus,
                attention,
                List.of()
        );
    }

    public HomeworkSubmissionSearchCriteria withActiveStudentIds(List<Long> studentIds) {
        return new HomeworkSubmissionSearchCriteria(
                studentKeyword,
                submitStatus,
                evaluationStatus,
                reviewStatus,
                attention,
                studentIds
        );
    }
}
