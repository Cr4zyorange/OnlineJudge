package com.onlinejudge.lab.domain;

import java.util.List;
import java.util.Optional;

public interface LabSubmissionRepository {
    LabSubmission save(LabSubmission submission);

    LabSubmission update(LabSubmission submission);

    Optional<LabSubmission> findLatestFinalByLabIdAndStudentId(long labId, long studentId);

    List<LabSubmission> findByLabIdAndStudentId(long labId, long studentId);
}
