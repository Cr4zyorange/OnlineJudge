package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabScoreSummaryView;

import java.time.LocalDateTime;

public record LabScoreResponse(
        long submissionId,
        Long reportId,
        Integer autoScore,
        Integer reportScore,
        Integer manualScore,
        int finalScore,
        String comment,
        boolean hasChangeLogs,
        LocalDateTime scoredAt,
        LocalDateTime updatedAt
) {
    public static LabScoreResponse from(LabScoreSummaryView score) {
        return new LabScoreResponse(
                score.submissionId(),
                score.reportId(),
                score.autoScore(),
                score.reportScore(),
                score.manualScore(),
                score.finalScore(),
                score.comment(),
                score.hasChangeLogs(),
                score.scoredAt(),
                score.updatedAt()
        );
    }
}
