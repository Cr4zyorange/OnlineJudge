package com.onlinejudge.lab.domain;

import java.time.LocalDateTime;

public record LabReport(
        long id,
        long labId,
        long studentId,
        Long submissionId,
        String fileId,
        String fileName,
        LabReportFileType fileType,
        long fileSize,
        int version,
        LabReportSubmitStatus submitStatus,
        Integer score,
        String comment,
        LocalDateTime submittedAt,
        Long scoredBy,
        LocalDateTime scoredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
