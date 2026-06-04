package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.ScoreLabReportCommand;

public record ScoreLabReportRequest(
        Integer score,
        String comment
) {
    public ScoreLabReportCommand toCommand() {
        return new ScoreLabReportCommand(score, comment);
    }
}
