package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabScoreChangeLog(
        long id,
        long scoreId,
        int oldFinalScore,
        int newFinalScore,
        String reason,
        long operatorId,
        LocalDateTime createdAt
) {
}
