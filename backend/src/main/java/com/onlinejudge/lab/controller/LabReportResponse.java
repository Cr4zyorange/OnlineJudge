package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabReportSummaryView;

import java.time.LocalDateTime;

public record LabReportResponse(
        long reportId,
        Long submissionId,
        String fileName,
        String fileType,
        long fileSize,
        int version,
        Integer score,
        String comment,
        LocalDateTime submittedAt,
        String downloadUrl
) {
    public static LabReportResponse from(LabReportSummaryView view) {
        return new LabReportResponse(
                view.reportId(),
                view.submissionId(),
                view.fileName(),
                view.fileType().name(),
                view.fileSize(),
                view.version(),
                view.score(),
                view.comment(),
                view.submittedAt(),
                view.downloadUrl()
        );
    }
}
