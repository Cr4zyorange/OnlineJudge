package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabReportSummaryView(
        long reportId,
        Long submissionId,
        String fileName,
        LabReportFileType fileType,
        long fileSize,
        int version,
        Integer score,
        String comment,
        LocalDateTime submittedAt,
        String downloadUrl
) {
    public static LabReportSummaryView from(LabReport report, String downloadUrl) {
        return new LabReportSummaryView(
                report.id(),
                report.submissionId(),
                report.fileName(),
                report.fileType(),
                report.fileSize(),
                report.version(),
                report.score(),
                report.comment(),
                report.submittedAt(),
                downloadUrl
        );
    }
}
