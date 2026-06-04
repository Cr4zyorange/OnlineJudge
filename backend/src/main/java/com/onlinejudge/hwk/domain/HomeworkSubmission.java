package com.onlinejudge.hwk.domain;

import com.onlinejudge.common.evaluation.EvaluationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HomeworkSubmission(
        long id,
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
        BigDecimal manualScore,
        BigDecimal finalScore,
        String comment,
        int version,
        boolean isFinal,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted
) {
    public HomeworkSubmission markHistorical(LocalDateTime updatedAt) {
        return new HomeworkSubmission(id, homeworkId, studentId, submitType, answerText, answerJson, fileUrl, language,
                submitStatus, evaluationStatus, reviewStatus, autoScore, manualScore, finalScore, comment, version,
                false, submittedAt, reviewedBy, reviewedAt, createdAt, updatedAt, deleted);
    }

    public HomeworkSubmission withEvaluationResult(
            EvaluationStatus evaluationStatus,
            HomeworkReviewStatus reviewStatus,
            Integer autoScore,
            Integer finalScore,
            LocalDateTime updatedAt
    ) {
        return new HomeworkSubmission(id, homeworkId, studentId, submitType, answerText, answerJson, fileUrl, language,
                submitStatus, evaluationStatus, reviewStatus, autoScore, manualScore, scoreToDecimal(finalScore), comment, version,
                isFinal, submittedAt, reviewedBy, reviewedAt, createdAt, updatedAt, deleted);
    }

    public HomeworkSubmission withReviewResult(
            BigDecimal manualScore,
            BigDecimal finalScore,
            String comment,
            long reviewedBy,
            LocalDateTime reviewedAt
    ) {
        return new HomeworkSubmission(id, homeworkId, studentId, submitType, answerText, answerJson, fileUrl, language,
                submitStatus, evaluationStatus, HomeworkReviewStatus.REVIEWED, autoScore, manualScore, finalScore,
                comment, version, isFinal, submittedAt, reviewedBy, reviewedAt, createdAt, reviewedAt, deleted);
    }

    private static BigDecimal scoreToDecimal(Integer score) {
        return score == null ? null : BigDecimal.valueOf(score);
    }
}
