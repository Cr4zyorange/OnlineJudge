package com.onlinejudge.lab.domain;

import java.util.Optional;

public interface LabReportRepository {
    LabReport save(LabReport report);

    LabReport updateScore(LabReport report);

    Optional<LabReport> findById(long reportId);

    Optional<LabReport> findLatestBySubmissionId(long submissionId);

    Optional<LabReport> findLatestByLabIdAndStudentId(long labId, long studentId);
}
