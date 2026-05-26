package com.onlinejudge.integration.grade;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DemoSourceGradeClient implements SourceGradeClient {
    @Override
    public List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId) {
        if (sourceType == SourceGradeType.LAB && sourceId == 301L) {
            return List.of(
                    grade(courseId, sourceType, sourceId, 601L, "90.00", "100.00", "SCORED"),
                    grade(courseId, sourceType, sourceId, 602L, "78.00", "100.00", "SCORED")
            );
        }
        if (sourceType == SourceGradeType.HWK && sourceId == 401L) {
            return List.of(
                    grade(courseId, sourceType, sourceId, 601L, "80.00", "100.00", "SCORED"),
                    grade(courseId, sourceType, sourceId, 602L, null, "100.00", "UNGRADED")
            );
        }
        return List.of();
    }

    private SourceGradeDTO grade(
            long courseId,
            SourceGradeType sourceType,
            long sourceId,
            long studentId,
            String score,
            String fullScore,
            String status
    ) {
        return new SourceGradeDTO(
                courseId,
                sourceType,
                sourceId,
                studentId,
                score == null ? null : new BigDecimal(score),
                new BigDecimal(fullScore),
                status,
                LocalDateTime.now()
        );
    }
}
