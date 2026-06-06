package com.onlinejudge.lab.domain;

import java.util.List;
import java.util.Optional;

public interface LabExperimentRepository {
    LabExperiment save(LabExperiment experiment);

    LabExperiment update(LabExperiment experiment);

    LabExperiment updateLifecycle(LabExperiment experiment);

    Optional<LabExperiment> findById(long labId);

    List<LabExperiment> findByCourseId(long courseId, LabExperimentStatus status);
}
