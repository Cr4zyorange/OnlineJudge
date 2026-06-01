package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkSubmissionResponse(
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        HomeworkReviewStatus reviewStatus,
        Integer autoScore,
        Integer manualScore,
        Integer finalScore,
        int version,
        @JsonProperty("final") boolean finalSubmission,
        LocalDateTime submittedAt
) {
    static HomeworkSubmissionResponse from(HomeworkSubmission submission) {
        return new HomeworkSubmissionResponse(
                submission.id(),
                submission.homeworkId(),
                submission.studentId(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.reviewStatus(),
                submission.autoScore(),
                submission.manualScore(),
                submission.finalScore(),
                submission.version(),
                submission.isFinal(),
                submission.submittedAt()
        );
    }
}
