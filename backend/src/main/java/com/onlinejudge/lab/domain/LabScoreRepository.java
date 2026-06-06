package com.onlinejudge.lab.domain;

import java.util.Optional;

public interface LabScoreRepository {
    LabScore save(LabScore score);

    LabScore update(LabScore score);

    Optional<LabScore> findBySubmissionId(long submissionId);
}
