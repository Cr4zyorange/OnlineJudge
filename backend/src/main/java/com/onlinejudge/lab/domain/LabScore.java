package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabScore(
        long id,
        long submissionId,
        Long reportId,
        long teacherId,
        Integer autoScore,
        Integer reportScore,
        Integer manualScore,
        int finalScore,
        String comment,
        LocalDateTime scoredAt,
        LocalDateTime updatedAt
) {
}
