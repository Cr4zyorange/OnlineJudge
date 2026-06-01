package com.onlinejudge.lab.domain;

import java.util.List;
import java.util.Optional;

public interface LabSubmissionRepository {
    LabSubmission save(LabSubmission submission);

    LabSubmission update(LabSubmission submission);

    java.util.Optional<LabSubmission> findById(long submissionId);

    Optional<LabSubmission> findLatestFinalByLabIdAndStudentId(long labId, long studentId);

    List<LabSubmission> findByLabId(long labId);

    List<LabSubmission> findByLabIdAndStudentId(long labId, long studentId);
}
