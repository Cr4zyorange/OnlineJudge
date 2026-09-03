package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkStatisticsAggregate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_statistics_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(
        scripts = {
                "file:../database/migrations/20260530_01_create_hwk_homework.sql",
                "file:../database/migrations/20260601_01_create_hwk_submission.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
@Sql(
        statements = {
                "DELETE FROM t_hwk_submission",
                "DELETE FROM t_hwk_homework"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class JdbcHomeworkStatisticsRepositoryTest {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    JdbcHomeworkStatisticsRepositoryTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void sqlAggregateUsesOnlyEligibleRosterSubmissionsAndEffectiveScore() {
        long homeworkId = insertHomework(100);
        insertSubmission(homeworkId, 601, "OBJECTIVE", "SUBMITTED", "ACCEPTED", "REVIEWED", 80,
                new BigDecimal("90.00"), 1, true, false);
        insertSubmission(homeworkId, 602, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("50.00"), 1, true, false);
        insertSubmission(homeworkId, 603, "CODE", "LATE", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false);
        insertSubmission(homeworkId, 601, "OBJECTIVE", "SUBMITTED", "ACCEPTED", "REVIEWED", 100,
                new BigDecimal("100.00"), 2, false, false);
        insertSubmission(homeworkId, 604, "TEXT", "REJECTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("100.00"), 1, true, false);
        insertSubmission(homeworkId, 605, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("100.00"), 1, true, true);
        insertSubmission(homeworkId, 999, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("100.00"), 1, true, false);

        JdbcHomeworkStatisticsRepository repository = new JdbcHomeworkStatisticsRepository(jdbcTemplate);
        HomeworkStatisticsAggregate aggregate = repository.aggregate(
                homeworkId,
                100,
                List.of(601L, 602L, 603L, 604L, 605L, 606L)
        );

        assertThat(aggregate.submittedCount()).isEqualTo(3);
        assertThat(aggregate.autoEvaluableCount()).isEqualTo(2);
        assertThat(aggregate.evaluatedCount()).isEqualTo(1);
        assertThat(aggregate.pendingEvaluationCount()).isEqualTo(1);
        assertThat(aggregate.pendingReviewCount()).isEqualTo(1);
        assertThat(aggregate.reviewedCount()).isEqualTo(1);
        assertThat(aggregate.scoredCount()).isEqualTo(2);
        assertThat(aggregate.averageScore()).isEqualByComparingTo("70.00");
        assertThat(aggregate.maxScore()).isEqualByComparingTo("90.00");
        assertThat(aggregate.minScore()).isEqualByComparingTo("50.00");
        assertThat(aggregate.score0To59()).isEqualTo(1);
        assertThat(aggregate.score60To69()).isZero();
        assertThat(aggregate.score70To79()).isZero();
        assertThat(aggregate.score80To89()).isZero();
        assertThat(aggregate.score90To100()).isEqualTo(1);

        List<Long> submittedStudentIds = repository.findSubmittedStudentIds(
                homeworkId,
                List.of(601L, 602L, 603L, 604L, 605L, 606L)
        );
        assertThat(submittedStudentIds).containsExactly(601L, 602L, 603L);
    }

    @Test
    void sqlAggregateReturnsFixedZerosWithoutIssuingAnEmptyInClause() {
        long homeworkId = insertHomework(100);
        insertSubmission(homeworkId, 999, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("100.00"), 1, true, false);

        JdbcHomeworkStatisticsRepository repository = new JdbcHomeworkStatisticsRepository(jdbcTemplate);
        HomeworkStatisticsAggregate aggregate = repository.aggregate(homeworkId, 100, List.of());

        assertThat(aggregate.submittedCount()).isZero();
        assertThat(aggregate.scoredCount()).isZero();
        assertThat(aggregate.averageScore()).isNull();
        assertThat(aggregate.score0To59()).isZero();
        assertThat(aggregate.score60To69()).isZero();
        assertThat(aggregate.score70To79()).isZero();
        assertThat(aggregate.score80To89()).isZero();
        assertThat(aggregate.score90To100()).isZero();
    }

    @Test
    void sqlAggregateExcludesScoresOutsideZeroAndHomeworkTotalFromScoreMetrics() {
        long homeworkId = insertHomework(50);
        insertSubmission(homeworkId, 601, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("25.00"), 1, true, false);
        insertSubmission(homeworkId, 602, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("-1.00"), 1, true, false);
        insertSubmission(homeworkId, 603, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("51.00"), 1, true, false);

        JdbcHomeworkStatisticsRepository repository = new JdbcHomeworkStatisticsRepository(jdbcTemplate);
        HomeworkStatisticsAggregate aggregate = repository.aggregate(
                homeworkId,
                50,
                List.of(601L, 602L, 603L)
        );

        assertThat(aggregate.submittedCount()).isEqualTo(3);
        assertThat(aggregate.scoredCount()).isEqualTo(1);
        assertThat(aggregate.averageScore()).isEqualByComparingTo("25.00");
        assertThat(aggregate.maxScore()).isEqualByComparingTo("25.00");
        assertThat(aggregate.minScore()).isEqualByComparingTo("25.00");
        assertThat(aggregate.score0To59()).isEqualTo(1);
        assertThat(aggregate.score60To69()).isZero();
        assertThat(aggregate.score70To79()).isZero();
        assertThat(aggregate.score80To89()).isZero();
        assertThat(aggregate.score90To100()).isZero();
        assertThat(aggregate.scoredCount()).isEqualTo(
                aggregate.score0To59()
                        + aggregate.score60To69()
                        + aggregate.score70To79()
                        + aggregate.score80To89()
                        + aggregate.score90To100()
        );
    }

    private long insertHomework(int totalScore) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                (course_id, chapter_id, title, description, type, status, total_score, deadline,
                 allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                 created_by, published_at, is_deleted, created_at, updated_at)
                VALUES (101, NULL, 'statistics homework', 'aggregate contract', 'TEXT', 'PUBLISHED', ?,
                        '2026-08-31 23:59:59', 1, 1, 1, NULL, 501, CURRENT_TIMESTAMP, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, totalScore);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_homework", Long.class);
    }

    private void insertSubmission(
            long homeworkId,
            long studentId,
            String submitType,
            String submitStatus,
            String evaluationStatus,
            String reviewStatus,
            Integer autoScore,
            BigDecimal finalScore,
            int version,
            boolean isFinal,
            boolean deleted
    ) {
        jdbcTemplate.update("""
                        INSERT INTO t_hwk_submission
                        (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                         submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                         comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at,
                         is_deleted)
                        VALUES (?, ?, ?, 'answer', NULL, NULL, NULL, ?, ?, ?, ?, NULL, ?, NULL, ?, ?,
                                CURRENT_TIMESTAMP, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                        """,
                homeworkId,
                studentId,
                submitType,
                submitStatus,
                evaluationStatus,
                reviewStatus,
                autoScore,
                finalScore,
                version,
                isFinal,
                deleted
        );
    }
}
