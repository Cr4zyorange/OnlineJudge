package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.onlinejudge.hwk.domain.HomeworkSubmission;

import java.time.LocalDateTime;

public record HomeworkSubmissionResponse(
        long id,
        long homeworkId,
        long studentId,
        String submitType,
        String answerText,
        String answerJson,
        String fileUrl,
        String language,
        String submitStatus,
        String evaluationStatus,
        String reviewStatus,
        Integer autoScore,
        Integer manualScore,
        Integer finalScore,
        String comment,
        @JsonProperty("final")
        boolean finalSubmission,
        boolean latest,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static HomeworkSubmissionResponse fromTeacherView(HomeworkSubmission submission, boolean latest) {
        return from(submission, submission.autoScore(), submission.manualScore(), submission.finalScore(), submission.comment(), latest);
    }

    static HomeworkSubmissionResponse fromStudentView(HomeworkSubmission submission) {
        return fromStudentView(submission, true);
    }

    static HomeworkSubmissionResponse fromStudentView(HomeworkSubmission submission, boolean latest) {
        return from(submission, null, null, null, null, latest);
    }

    private static HomeworkSubmissionResponse from(
            HomeworkSubmission submission,
            Integer autoScore,
            Integer manualScore,
            Integer finalScore,
            String comment,
            boolean latest
    ) {
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
                autoScore,
                manualScore,
                finalScore,
                comment,
                submission.isFinal(),
                latest,
                submission.submittedAt(),
                submission.createdAt(),
                submission.updatedAt()
        );
    }
}
