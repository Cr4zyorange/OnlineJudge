package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;

public record HomeworkSubmission(
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
        boolean isFinal,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
