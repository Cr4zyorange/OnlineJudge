package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.onlinejudge.hwk.domain.HomeworkEvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HomeworkSubmissionResponse(
        long id,
        long homeworkId,
        long studentId,
        HomeworkSubmitType submitType,
        String answerText,
        String answerJson,
        String fileUrl,
        String language,
        HomeworkSubmitStatus submitStatus,
        HomeworkEvaluationStatus evaluationStatus,
        HomeworkReviewStatus reviewStatus,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal finalScore,
        String comment,
        @JsonProperty("isLatest")
        boolean latestSubmission,
        @JsonProperty("isFinal")
        boolean finalSubmission,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt
) {
    static HomeworkSubmissionResponse from(HomeworkSubmission submission) {
        return new HomeworkSubmissionResponse(
                submission.id(),
                submission.homeworkId(),
                submission.studentId(),
                submission.submitType(),
                submission.answerText(),
                submission.answerJson(),
                submission.fileUrl(),
                submission.language(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.reviewStatus(),
                submission.autoScore(),
                submission.manualScore(),
                submission.finalScore(),
                submission.comment(),
                submission.latestSubmission(),
                submission.finalSubmission(),
                submission.submittedAt(),
                submission.reviewedBy(),
                submission.reviewedAt()
        );
    }
}
