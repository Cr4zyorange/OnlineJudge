package com.onlinejudge.lab.domain;

public record ScoreLabReportCommand(
        Integer score,
        String comment
) {
}
