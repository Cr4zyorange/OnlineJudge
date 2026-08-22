package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkStatisticsAggregate;
import com.onlinejudge.hwk.domain.HomeworkStatisticsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Repository
public class JdbcHomeworkStatisticsRepository implements HomeworkStatisticsRepository {
    private static final String AUTOMATIC_TYPES = "'OBJECTIVE', 'CODE'";
    private static final String MANUAL_TYPES = "'TEXT', 'FILE'";
    private static final String PENDING_EVALUATION_STATUSES = "'NONE', 'PENDING', 'RUNNING'";
    private static final String TERMINAL_EVALUATION_STATUSES = """
            'ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR',
            'TIME_LIMIT_EXCEEDED', 'SYSTEM_ERROR'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkStatisticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HomeworkStatisticsAggregate aggregate(long homeworkId, int totalScore, List<Long> activeStudentIds) {
        List<Long> studentIds = normalizeStudentIds(activeStudentIds);
        if (studentIds.isEmpty()) {
            return HomeworkStatisticsAggregate.empty();
        }
        String placeholders = placeholders(studentIds.size());
        String sql = """
                SELECT COUNT(*) AS submitted_count,
                       COALESCE(SUM(CASE WHEN submit_type IN (%s) THEN 1 ELSE 0 END), 0) AS auto_evaluable_count,
                       COALESCE(SUM(CASE
                           WHEN submit_type IN (%s) AND evaluation_status IN (%s) THEN 1 ELSE 0
                       END), 0) AS evaluated_count,
                       COALESCE(SUM(CASE
                           WHEN submit_type IN (%s) AND evaluation_status IN (%s) THEN 1 ELSE 0
                       END), 0) AS pending_evaluation_count,
                       COALESCE(SUM(CASE
                           WHEN review_status IN ('UNREVIEWED', 'NEED_REVIEW')
                            AND (submit_type IN (%s)
                                 OR (submit_type IN (%s) AND evaluation_status IN (%s)))
                           THEN 1 ELSE 0
                       END), 0) AS pending_review_count,
                       COALESCE(SUM(CASE WHEN review_status = 'REVIEWED' THEN 1 ELSE 0 END), 0) AS reviewed_count,
                       COUNT(CASE WHEN normalized_score >= 0 AND normalized_score <= 100 THEN effective_score END) AS scored_count,
                       AVG(CASE WHEN normalized_score >= 0 AND normalized_score <= 100 THEN effective_score END) AS average_score,
                       MAX(CASE WHEN normalized_score >= 0 AND normalized_score <= 100 THEN effective_score END) AS max_score,
                       MIN(CASE WHEN normalized_score >= 0 AND normalized_score <= 100 THEN effective_score END) AS min_score,
                       COALESCE(SUM(CASE WHEN normalized_score >= 0 AND normalized_score < 60 THEN 1 ELSE 0 END), 0) AS score_0_59,
                       COALESCE(SUM(CASE WHEN normalized_score >= 60 AND normalized_score < 70 THEN 1 ELSE 0 END), 0) AS score_60_69,
                       COALESCE(SUM(CASE WHEN normalized_score >= 70 AND normalized_score < 80 THEN 1 ELSE 0 END), 0) AS score_70_79,
                       COALESCE(SUM(CASE WHEN normalized_score >= 80 AND normalized_score < 90 THEN 1 ELSE 0 END), 0) AS score_80_89,
                       COALESCE(SUM(CASE WHEN normalized_score >= 90 AND normalized_score <= 100 THEN 1 ELSE 0 END), 0) AS score_90_100
                FROM (
                    SELECT submit_type,
                           evaluation_status,
                           review_status,
                           COALESCE(final_score, auto_score) AS effective_score,
                           COALESCE(final_score, auto_score) * 100 / ? AS normalized_score
                    FROM t_hwk_submission
                    WHERE homework_id = ?
                      AND is_final = TRUE
                      AND is_deleted = FALSE
                      AND submit_status IN ('SUBMITTED', 'LATE')
                      AND student_id IN (%s)
                ) eligible
                """.formatted(
                AUTOMATIC_TYPES,
                AUTOMATIC_TYPES,
                TERMINAL_EVALUATION_STATUSES,
                AUTOMATIC_TYPES,
                PENDING_EVALUATION_STATUSES,
                MANUAL_TYPES,
                AUTOMATIC_TYPES,
                TERMINAL_EVALUATION_STATUSES,
                placeholders
        );
        List<Object> parameters = new ArrayList<>();
        parameters.add(totalScore);
        parameters.add(homeworkId);
        parameters.addAll(studentIds);
        return jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNum) -> new HomeworkStatisticsAggregate(
                        resultSet.getInt("submitted_count"),
                        resultSet.getInt("auto_evaluable_count"),
                        resultSet.getInt("evaluated_count"),
                        resultSet.getInt("pending_evaluation_count"),
                        resultSet.getInt("pending_review_count"),
                        resultSet.getInt("reviewed_count"),
                        resultSet.getInt("scored_count"),
                        average(resultSet.getBigDecimal("average_score")),
                        resultSet.getBigDecimal("max_score"),
                        resultSet.getBigDecimal("min_score"),
                        resultSet.getInt("score_0_59"),
                        resultSet.getInt("score_60_69"),
                        resultSet.getInt("score_70_79"),
                        resultSet.getInt("score_80_89"),
                        resultSet.getInt("score_90_100")
                ),
                parameters.toArray()
        );
    }

    @Override
    public List<Long> findSubmittedStudentIds(long homeworkId, List<Long> activeStudentIds) {
        List<Long> studentIds = normalizeStudentIds(activeStudentIds);
        if (studentIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT student_id
                FROM t_hwk_submission
                WHERE homework_id = ?
                  AND is_final = TRUE
                  AND is_deleted = FALSE
                  AND submit_status IN ('SUBMITTED', 'LATE')
                  AND student_id IN (%s)
                ORDER BY student_id ASC
                """.formatted(placeholders(studentIds.size()));
        List<Object> parameters = new ArrayList<>();
        parameters.add(homeworkId);
        parameters.addAll(studentIds);
        return jdbcTemplate.queryForList(sql, Long.class, parameters.toArray());
    }

    private static BigDecimal average(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static List<Long> normalizeStudentIds(List<Long> studentIds) {
        if (studentIds == null) {
            return List.of();
        }
        return studentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String placeholders(int size) {
        return String.join(", ", Collections.nCopies(size, "?"));
    }
}
