package com.onlinejudge.lab.domain;

public record ScoreLabSubmissionCommand(
        Integer manualScore,
        Integer reportScore,
        Integer finalScore,
        String comment,
        String changeReason
) {
}
