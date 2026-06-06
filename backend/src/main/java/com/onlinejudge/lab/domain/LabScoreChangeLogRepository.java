package com.onlinejudge.lab.domain;

public interface LabScoreChangeLogRepository {
    LabScoreChangeLog save(LabScoreChangeLog changeLog);

    int countByScoreId(long scoreId);
}
