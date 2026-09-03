package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** SQL-only read model for API-HWK-15; all aggregates stay scoped to the active course roster. */
@Repository
public class HomeworkStatisticsRepository {
    private final JdbcTemplate jdbc;

    public HomeworkStatisticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String creatorFor(long homeworkId) {
        return jdbc.queryForObject("SELECT created_by FROM assessment_homework WHERE id = ?", String.class, homeworkId);
    }

    public Aggregate aggregate(long homeworkId, String courseId, String creator, BigDecimal totalScore) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total_student_count,
                       COALESCE(SUM(CASE WHEN submission.student_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS submitted_count,
                       COALESCE(SUM(CASE WHEN submission.submit_type IN ('OBJECTIVE', 'CODE') THEN 1 ELSE 0 END), 0) AS auto_evaluable_count,
                       COALESCE(SUM(CASE WHEN submission.submit_type IN ('OBJECTIVE', 'CODE')
                                           AND submission.evaluation_status IN ('NONE', 'PENDING', 'RUNNING') THEN 1 ELSE 0 END), 0) AS pending_evaluation_count,
                       COALESCE(SUM(CASE WHEN submission.submit_type IN ('OBJECTIVE', 'CODE')
                                           AND submission.evaluation_status IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED', 'SYSTEM_ERROR') THEN 1 ELSE 0 END), 0) AS evaluated_count,
                       COALESCE(SUM(CASE WHEN submission.review_status IN ('UNREVIEWED', 'NEED_REVIEW')
                                           AND (submission.submit_type IN ('TEXT', 'FILE')
                                                OR (submission.submit_type IN ('OBJECTIVE', 'CODE')
                                                    AND submission.evaluation_status IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED', 'SYSTEM_ERROR')))
                                        THEN 1 ELSE 0 END), 0) AS pending_review_count,
                       COALESCE(SUM(CASE WHEN submission.review_status = 'REVIEWED' THEN 1 ELSE 0 END), 0) AS reviewed_count,
                       COALESCE(SUM(CASE WHEN COALESCE(submission.final_score, submission.auto_score) IS NOT NULL THEN 1 ELSE 0 END), 0) AS scored_count,
                       AVG(COALESCE(submission.final_score, submission.auto_score)) AS average_score,
                       MAX(COALESCE(submission.final_score, submission.auto_score)) AS max_score,
                       MIN(COALESCE(submission.final_score, submission.auto_score)) AS min_score,
                       COALESCE(SUM(CASE WHEN COALESCE(submission.final_score, submission.auto_score) IS NOT NULL
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? < 60 THEN 1 ELSE 0 END), 0) AS score_0_59,
                       COALESCE(SUM(CASE WHEN COALESCE(submission.final_score, submission.auto_score) IS NOT NULL
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? >= 60
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? < 70 THEN 1 ELSE 0 END), 0) AS score_60_69,
                       COALESCE(SUM(CASE WHEN COALESCE(submission.final_score, submission.auto_score) IS NOT NULL
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? >= 70
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? < 80 THEN 1 ELSE 0 END), 0) AS score_70_79,
                       COALESCE(SUM(CASE WHEN COALESCE(submission.final_score, submission.auto_score) IS NOT NULL
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? >= 80
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? < 90 THEN 1 ELSE 0 END), 0) AS score_80_89,
                       COALESCE(SUM(CASE WHEN COALESCE(submission.final_score, submission.auto_score) IS NOT NULL
                                           AND COALESCE(submission.final_score, submission.auto_score) * 100 / ? >= 90 THEN 1 ELSE 0 END), 0) AS score_90_100
                  FROM assessment_course_member_projection member
                  LEFT JOIN assessment_homework_submission submission
                    ON submission.homework_id = ?
                   AND submission.student_id = member.user_id
                   AND submission.is_final = TRUE
                   AND submission.submit_status IN ('SUBMITTED', 'LATE')
                 WHERE member.course_id = ?
                   AND member.membership_status = 'ACTIVE'
                   AND member.user_id <> ?
                """, (rs, ignored) -> new Aggregate(
                        rs.getInt("total_student_count"), rs.getInt("submitted_count"),
                        rs.getInt("auto_evaluable_count"), rs.getInt("pending_evaluation_count"),
                        rs.getInt("evaluated_count"), rs.getInt("pending_review_count"), rs.getInt("reviewed_count"),
                        rs.getInt("scored_count"), rs.getBigDecimal("average_score"), rs.getBigDecimal("max_score"),
                        rs.getBigDecimal("min_score"), rs.getInt("score_0_59"), rs.getInt("score_60_69"),
                        rs.getInt("score_70_79"), rs.getInt("score_80_89"), rs.getInt("score_90_100")),
                totalScore, totalScore, totalScore, totalScore, totalScore, totalScore, totalScore, totalScore,
                homeworkId, courseId, creator);
    }

    public int unsubmittedTotal(long homeworkId, String courseId, String creator) {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_course_member_projection member
                 WHERE member.course_id = ?
                   AND member.membership_status = 'ACTIVE'
                   AND member.user_id <> ?
                   AND NOT EXISTS (
                       SELECT 1 FROM assessment_homework_submission submission
                        WHERE submission.homework_id = ?
                          AND submission.student_id = member.user_id
                          AND submission.is_final = TRUE
                          AND submission.submit_status IN ('SUBMITTED', 'LATE')
                   )
                """, Integer.class, courseId, creator, homeworkId);
        return total == null ? 0 : total;
    }

    public List<String> unsubmittedStudentIds(long homeworkId, String courseId, String creator, int size, long offset) {
        return jdbc.queryForList("""
                SELECT member.user_id FROM assessment_course_member_projection member
                 WHERE member.course_id = ?
                   AND member.membership_status = 'ACTIVE'
                   AND member.user_id <> ?
                   AND NOT EXISTS (
                       SELECT 1 FROM assessment_homework_submission submission
                        WHERE submission.homework_id = ?
                          AND submission.student_id = member.user_id
                          AND submission.is_final = TRUE
                          AND submission.submit_status IN ('SUBMITTED', 'LATE')
                   )
                 ORDER BY member.user_id
                 LIMIT ? OFFSET ?
                """, String.class, courseId, creator, homeworkId, size, offset);
    }

    public record Aggregate(int totalStudentCount, int submittedCount, int autoEvaluableCount, int pendingEvaluationCount,
                            int evaluatedCount, int pendingReviewCount, int reviewedCount, int scoredCount,
                            BigDecimal averageScore, BigDecimal maxScore, BigDecimal minScore,
                            int score0To59, int score60To69, int score70To79, int score80To89, int score90To100) { }
}
