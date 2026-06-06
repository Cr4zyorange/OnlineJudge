package com.onlinejudge.integration.grade;

import java.util.List;
import java.util.Optional;

public interface SourceGradeProvider {
    boolean supports(SourceGradeType sourceType);

    Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId);
}
