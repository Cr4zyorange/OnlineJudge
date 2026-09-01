package com.onlinejudge.gradeservice;

import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeType;
import com.onlinejudge.gradeservice.service.SourceProjectionGapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
class ProjectionSourceGradeClientTest {
    @Autowired SourceGradeClient sourceGrades;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM grade_source_projection_gap");
        jdbc.update("DELETE FROM grade_source_projection");
    }

    @Test
    void calculationReadsTheCompleteLocalProjectionWithoutAssessmentOnline() {
        insertProjection("LAB:77:601", 101, "LAB", 77, 601, "86", 2);

        var result = sourceGrades.findSourceGrades(101, SourceGradeType.LAB, 77);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().studentId()).isEqualTo(601);
        assertThat(result.getFirst().score()).isEqualByComparingTo("86");
    }

    @Test
    void aProjectionGapNeverProducesAFalseCompleteCalculation() {
        insertProjection("HWK:88:602", 101, "HWK", 88, 602, "70", 1);
        jdbc.update("""
                INSERT INTO grade_source_projection_gap
                    (aggregate_id, expected_version, observed_version, correlation_id, updated_at)
                VALUES ('HWK:88:602', 2, 3, '11111111-1111-4111-8111-111111111111', CURRENT_TIMESTAMP)
                """);

        assertThatThrownBy(() -> sourceGrades.findSourceGrades(101, SourceGradeType.HWK, 88))
                .isInstanceOf(SourceProjectionGapException.class)
                .hasMessageContaining("revision gap");
    }

    @Test
    void detectsAnInitialRevisionGapBeforeAnyProjectionRowExists() {
        jdbc.update("""
                INSERT INTO grade_source_projection_gap
                    (aggregate_id, expected_version, observed_version, correlation_id, updated_at)
                VALUES ('LAB:71:11', 1, 2, '11111111-1111-4111-8111-111111111111', CURRENT_TIMESTAMP)
                """);

        assertThatThrownBy(() -> sourceGrades.findSourceGrades(101, SourceGradeType.LAB, 71))
                .isInstanceOf(SourceProjectionGapException.class);
    }

    private void insertProjection(String aggregateId, long courseId, String sourceType, long sourceId,
                                  long studentId, String score, long sourceVersion) {
        jdbc.update("""
                INSERT INTO grade_source_projection
                    (aggregate_id, course_id, source_type, source_id, student_id, score, full_score,
                     source_status, source_version, occurred_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 100, 'SCORED', ?, ?, CURRENT_TIMESTAMP)
                """, aggregateId, courseId, sourceType, sourceId, studentId, score, sourceVersion,
                Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")));
    }
}
