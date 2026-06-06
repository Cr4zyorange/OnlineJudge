package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.ScoreLabSubmissionCommand;

public record ScoreLabSubmissionRequest(
        Integer manualScore,
        Integer reportScore,
        Integer finalScore,
        String comment,
        String changeReason
) {
    ScoreLabSubmissionCommand toCommand() {
        return new ScoreLabSubmissionCommand(
                manualScore,
                reportScore,
                finalScore,
                comment,
                changeReason
        );
    }
}
