package com.onlinejudge.lab.domain;

import java.util.Optional;

public interface LabSubmissionSourceFileRepository {
    LabSubmissionSourceFile save(LabSubmissionSourceFile sourceFile);

    Optional<LabSubmissionSourceFile> findBySubmissionId(long submissionId);
}
