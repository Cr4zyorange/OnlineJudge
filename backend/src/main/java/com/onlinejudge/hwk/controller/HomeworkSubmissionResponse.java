package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkSubmissionResponse(
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkType submitType,
        String answerText,
        String answerJson,
        boolean hasAttachment,
        HomeworkSubmissionAttachmentResponse attachment,
        String language,
        HomeworkSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        HomeworkReviewStatus reviewStatus,
        Integer autoScore,
        BigDecimal manualScore,
        BigDecimal finalScore,
        String comment,
        int version,
        @JsonProperty("final") boolean finalSubmission,
        LocalDateTime submittedAt
) {
    static HomeworkSubmissionResponse from(HomeworkSubmission submission) {
        return fromTeacherView(submission);
    }

    static HomeworkSubmissionResponse fromTeacherView(HomeworkSubmission submission) {
        return fromTeacherView(submission, null);
    }

    static HomeworkSubmissionResponse fromTeacherView(
            HomeworkSubmission submission,
            com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentView attachment
    ) {
        return new HomeworkSubmissionResponse(
                submission.id(),
                submission.homeworkId(),
                submission.studentId(),
                submission.submitType(),
                submission.answerText(),
                submission.answerJson(),
                attachment != null || hasLegacyAttachment(submission),
                HomeworkSubmissionAttachmentResponse.from(attachment),
                submission.language(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.reviewStatus(),
                submission.autoScore(),
                submission.manualScore(),
                submission.finalScore(),
                submission.comment(),
                submission.version(),
                submission.isFinal(),
                submission.submittedAt()
        );
    }

    static HomeworkSubmissionResponse fromStudentView(Homework homework, HomeworkSubmission submission) {
        return fromStudentView(homework, submission, null);
    }

    static HomeworkSubmissionResponse fromStudentView(
            Homework homework,
            HomeworkSubmission submission,
            com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentView attachment
    ) {
        boolean scorePublished = homework.status() == HomeworkStatus.SCORE_PUBLISHED
                || homework.status() == HomeworkStatus.ARCHIVED;
        boolean evaluationVisible = homework.showEvaluationBeforePublish() || scorePublished;
        return new HomeworkSubmissionResponse(
                submission.id(),
                submission.homeworkId(),
                submission.studentId(),
                submission.submitType(),
                submission.answerText(),
                submission.answerJson(),
                attachment != null || hasLegacyAttachment(submission),
                HomeworkSubmissionAttachmentResponse.from(attachment),
                submission.language(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.reviewStatus(),
                evaluationVisible ? submission.autoScore() : null,
                scorePublished ? submission.manualScore() : null,
                scorePublished ? submission.finalScore() : null,
                scorePublished ? submission.comment() : null,
                submission.version(),
                submission.isFinal(),
                submission.submittedAt()
        );
    }

    private static boolean hasLegacyAttachment(HomeworkSubmission submission) {
        return submission.fileUrl() != null && !submission.fileUrl().isBlank();
    }
}
