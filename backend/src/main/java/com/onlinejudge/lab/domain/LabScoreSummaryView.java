package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabScoreSummaryView(
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
    public static LabScoreSummaryView from(LabScore score, boolean hasChangeLogs) {
        return new LabScoreSummaryView(
                score.submissionId(),
                score.reportId(),
                score.autoScore(),
                score.reportScore(),
                score.manualScore(),
                score.finalScore(),
                score.comment(),
                hasChangeLogs,
                score.scoredAt(),
                score.updatedAt()
        );
    }
}
