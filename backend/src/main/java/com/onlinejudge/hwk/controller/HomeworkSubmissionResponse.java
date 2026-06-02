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

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkSubmissionResponse(
        long submissionId,
        long homeworkId,
        long studentId,
        HomeworkType submitType,
        String answerText,
        String answerJson,
        String fileUrl,
        String language,
        HomeworkSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        HomeworkReviewStatus reviewStatus,
        Integer autoScore,
        Integer manualScore,
        Integer finalScore,
        String comment,
        int version,
        @JsonProperty("final") boolean finalSubmission,
        LocalDateTime submittedAt
) {
    static HomeworkSubmissionResponse from(HomeworkSubmission submission) {
        return fromTeacherView(submission);
    }

    static HomeworkSubmissionResponse fromTeacherView(HomeworkSubmission submission) {
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
                submission.version(),
                submission.isFinal(),
                submission.submittedAt()
        );
    }

    static HomeworkSubmissionResponse fromStudentView(Homework homework, HomeworkSubmission submission) {
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
                submission.fileUrl(),
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
}
