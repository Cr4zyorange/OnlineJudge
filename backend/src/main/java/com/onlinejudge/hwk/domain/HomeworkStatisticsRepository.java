package com.onlinejudge.hwk.domain;

import java.util.List;

public interface HomeworkStatisticsRepository {
    HomeworkStatisticsAggregate aggregate(long homeworkId, int totalScore, List<Long> activeStudentIds);

    List<Long> findSubmittedStudentIds(long homeworkId, List<Long> activeStudentIds);
}
