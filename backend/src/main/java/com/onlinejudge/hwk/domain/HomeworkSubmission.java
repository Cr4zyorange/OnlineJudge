package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HomeworkSubmission(
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
        boolean latestSubmission,
        boolean finalSubmission,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
