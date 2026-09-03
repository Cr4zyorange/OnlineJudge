package com.onlinejudge.gradeservice.service;

import com.onlinejudge.grd.service.GradeResultTraceRecorder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Stores an immutable calculation evidence row instead of resolving current source data during audit. */
@Component
public class GradeCalculationTraceRecorder implements GradeResultTraceRecorder {
    private final JdbcTemplate jdbc;

    public GradeCalculationTraceRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void record(long courseId, long calculationBatchId) {
        refreshRuleVersions(courseId);
        jdbc.update("""
                INSERT INTO grade_result_trace
                    (calculation_batch_id, grade_record_id, course_id, student_id, grade_item_id,
                     source_version, rule_version, grade_status, raw_score, weighted_score, recorded_at)
                SELECT ?, r.id, r.course_id, r.student_id, r.grade_item_id,
                       p.source_version, rv.rule_version, r.grade_status, r.raw_score, r.weighted_score, CURRENT_TIMESTAMP
                  FROM t_grade_record r
                  JOIN t_grade_item i ON i.id=r.grade_item_id AND i.course_id=r.course_id
                  JOIN grade_rule_version rv ON rv.grade_item_id=i.id
             LEFT JOIN grade_source_projection p
                    ON CAST(p.course_id AS DECIMAL(19, 0))=r.course_id
                   AND p.source_type=r.source_type
                   AND CAST(p.source_id AS DECIMAL(19, 0))=r.source_id
                   AND CAST(p.student_id AS DECIMAL(19, 0))=r.student_id
                 WHERE r.course_id=?
                ON DUPLICATE KEY UPDATE calculation_batch_id=VALUES(calculation_batch_id)
                """, calculationBatchId, courseId);
    }

    private void refreshRuleVersions(long courseId) {
        jdbc.query("""
                SELECT id, name, source_type, source_id, full_score, weight,
                       included_in_final, enabled, sort_order, deleted
                  FROM t_grade_item WHERE course_id=?
                """, (rs, ignored) -> new RuleSnapshot(
                rs.getLong("id"), fingerprint(
                        rs.getString("name"), rs.getString("source_type"), rs.getString("source_id"),
                        rs.getString("full_score"), rs.getString("weight"), rs.getString("included_in_final"),
                        rs.getString("enabled"), rs.getString("sort_order"), rs.getString("deleted"))), courseId)
                .forEach(rule -> {
                    int changed = jdbc.update("""
                            UPDATE grade_rule_version
                               SET rule_version=rule_version+1, rule_fingerprint=?, updated_at=CURRENT_TIMESTAMP
                             WHERE grade_item_id=? AND rule_fingerprint<>?
                            """, rule.fingerprint(), rule.gradeItemId(), rule.fingerprint());
                    if (changed == 0) {
                        jdbc.update("""
                                INSERT INTO grade_rule_version (grade_item_id, rule_version, rule_fingerprint, updated_at)
                                VALUES (?, 1, ?, CURRENT_TIMESTAMP)
                                ON DUPLICATE KEY UPDATE grade_item_id=VALUES(grade_item_id)
                                """, rule.gradeItemId(), rule.fingerprint());
                    }
                });
    }

    private static String fingerprint(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                digest.update(String.valueOf(field).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record RuleSnapshot(long gradeItemId, String fingerprint) { }
}
