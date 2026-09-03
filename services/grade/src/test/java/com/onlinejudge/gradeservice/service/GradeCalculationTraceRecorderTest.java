package com.onlinejudge.gradeservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
class GradeCalculationTraceRecorderTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GradeCalculationTraceRecorder recorder;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM grade_result_trace");
        jdbc.update("DELETE FROM grade_rule_version");
        jdbc.update("DELETE FROM grade_source_projection");
        jdbc.update("DELETE FROM t_grade_record");
        jdbc.update("DELETE FROM t_grade_item");
    }

    @Test
    void freezesSourceRevisionRuleVersionAndCalculationBatchForEachCalculatedRecord() {
        jdbc.update("""
                INSERT INTO t_grade_item
                    (id, course_id, name, source_type, source_id, full_score, weight, included_in_final,
                     enabled, sort_order, created_by, deleted, created_at, updated_at)
                VALUES (501, 41, 'Lab 1', 'LAB', 71, 100, 0.5, TRUE, TRUE, 1, 7, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO t_grade_record
                    (id, course_id, student_id, grade_item_id, source_type, source_id, raw_score, weighted_score,
                     grade_status, publish_status, created_at, updated_at)
                VALUES (601, 41, 11, 501, 'LAB', 71, 90, 45, 'SCORED', 'UNPUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO grade_source_projection
                    (aggregate_id, course_id, source_type, source_id, student_id, score, full_score,
                     source_status, source_version, occurred_at, updated_at)
                VALUES ('LAB:71:11', '41', 'LAB', '71', '11', 90, 100, 'SCORED', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        recorder.record(41, 9001);

        var trace = jdbc.queryForMap("""
                SELECT source_version, rule_version, calculation_batch_id, raw_score, weighted_score
                  FROM grade_result_trace
                 WHERE course_id=41 AND student_id=11 AND grade_item_id=501
                """);
        assertThat(((Number) trace.get("source_version")).longValue()).isEqualTo(9);
        assertThat(((Number) trace.get("rule_version")).longValue()).isEqualTo(1);
        assertThat(((Number) trace.get("calculation_batch_id")).longValue()).isEqualTo(9001);
        assertThat((BigDecimal) trace.get("raw_score")).isEqualByComparingTo("90");
        assertThat((BigDecimal) trace.get("weighted_score")).isEqualByComparingTo("45");

        jdbc.update("UPDATE t_grade_item SET weight=0.6 WHERE id=501");
        recorder.record(41, 9002);
        Long nextRuleVersion = jdbc.queryForObject("""
                SELECT rule_version FROM grade_result_trace
                 WHERE calculation_batch_id=9002 AND grade_record_id=601
                """, Long.class);
        assertThat(nextRuleVersion).isEqualTo(2);
    }

    @Test
    void usesMysqlCompatibleDecimalCastsWhenJoiningStringSourceProjectionKeys() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/onlinejudge/gradeservice/service/GradeCalculationTraceRecorder.java"));

        assertThat(source)
                .contains("CAST(p.course_id AS DECIMAL(19, 0))=r.course_id")
                .contains("CAST(p.source_id AS DECIMAL(19, 0))=r.source_id")
                .contains("CAST(p.student_id AS DECIMAL(19, 0))=r.student_id")
                .doesNotContain("AS BIGINT");
    }
}
