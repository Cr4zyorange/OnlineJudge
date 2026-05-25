package com.onlinejudge.integration.grade;

import java.util.List;

public interface SourceGradeClient {
    List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId);
}
