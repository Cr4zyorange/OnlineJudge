package com.onlinejudge.hwk.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttention;
import com.onlinejudge.hwk.domain.HomeworkSubmissionSearchCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_attention_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
class JdbcHomeworkSubmissionRepositoryAttentionTest {
    private final JdbcTemplate jdbcTemplate;
    private final JdbcHomeworkSubmissionRepository repository;

    @Autowired
    JdbcHomeworkSubmissionRepositoryAttentionTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = new JdbcHomeworkSubmissionRepository(jdbcTemplate);
    }

    @Test
    void evaluationAttentionCombinesEligibilityAndExistingFiltersBeforeStablePaging() {
        long homeworkId = insertHomework();
        long firstId = insertSubmission(homeworkId, 601, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", 1,
                true, false, "2026-08-22 10:00:00");
        long secondId = insertSubmission(homeworkId, 602, "OBJECTIVE", "LATE", "PENDING", "UNREVIEWED", 1,
                true, false, "2026-08-22 10:00:00");
        insertSubmission(homeworkId, 603, "CODE", "SUBMITTED", "RUNNING", "NEED_REVIEW", 1,
                true, false, "2026-08-22 09:00:00");
        insertSubmission(homeworkId, 604, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", 1,
                true, false, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 999, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", 1,
                true, false, "2026-08-22 08:00:00");

        HomeworkSubmissionSearchCriteria criteria = attentionCriteria(
                "EVALUATION_PENDING",
                EvaluationStatus.PENDING,
                List.of(601L, 602L, 603L, 604L)
        );
        PageResponse<HomeworkSubmission> page = repository.findByHomeworkId(homeworkId, criteria, 1, 2);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.list()).extracting(HomeworkSubmission::id).containsExactly(secondId, firstId);
    }

    @Test
    void reviewAttentionIncludesManualTypesAndOnlyTerminalAutomaticTypes() {
        long homeworkId = insertHomework();
        long textId = insertSubmission(homeworkId, 601, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", 1,
                true, false, "2026-08-22 10:00:00");
        long codeId = insertSubmission(homeworkId, 602, "CODE", "SUBMITTED", "SYSTEM_ERROR", "NEED_REVIEW", 1,
                true, false, "2026-08-22 09:00:00");
        insertSubmission(homeworkId, 603, "CODE", "SUBMITTED", "RUNNING", "NEED_REVIEW", 1,
                true, false, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 604, "FILE", "REJECTED", "NONE", "UNREVIEWED", 1,
                true, false, "2026-08-22 07:00:00");

        HomeworkSubmissionSearchCriteria criteria = attentionCriteria(
                "REVIEW_PENDING",
                null,
                List.of(601L, 602L, 603L, 604L)
        );
        PageResponse<HomeworkSubmission> page = repository.findByHomeworkId(homeworkId, criteria, 1, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.list()).extracting(HomeworkSubmission::id).containsExactly(textId, codeId);
    }

    @Test
    void absentAttentionKeepsHistoricalRejectedAndNonRosterSubmissionsVisible() {
        long homeworkId = insertHomework();
        insertSubmission(homeworkId, 601, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", 1,
                false, false, "2026-08-22 10:00:00");
        insertSubmission(homeworkId, 602, "TEXT", "REJECTED", "NONE", "UNREVIEWED", 1,
                true, false, "2026-08-22 09:00:00");
        insertSubmission(homeworkId, 999, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", 1,
                true, false, "2026-08-22 08:00:00");

        PageResponse<HomeworkSubmission> page = repository.findByHomeworkId(
                homeworkId,
                HomeworkSubmissionSearchCriteria.of(null, null, null, null),
                1,
                20
        );

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.list()).hasSize(3);
    }

    @Test
    void attentionPagingDoesNotWrapTheOffsetForTheLargestIntPage() {
        long homeworkId = insertHomework();
        insertSubmission(homeworkId, 601, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", 1,
                true, false, "2026-08-22 10:00:00");

        PageResponse<HomeworkSubmission> page = repository.findByHomeworkId(
                homeworkId,
                attentionCriteria("EVALUATION_PENDING", null, List.of(601L)),
                Integer.MAX_VALUE,
                100
        );

        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.list()).isEmpty();
    }

    private HomeworkSubmissionSearchCriteria attentionCriteria(
            String attentionName,
            EvaluationStatus evaluationStatus,
            List<Long> activeStudentIds
    ) {
        return HomeworkSubmissionSearchCriteria.of(
                        null,
                        null,
                        evaluationStatus,
                        null,
                        HomeworkSubmissionAttention.valueOf(attentionName)
                )
                .withActiveStudentIds(activeStudentIds);
    }

    private long insertHomework() {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                (course_id, chapter_id, title, description, type, status, total_score, deadline,
                 allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                 created_by, published_at, is_deleted, created_at, updated_at)
                VALUES (101, NULL, 'attention homework', 'queue contract', 'TEXT', 'PUBLISHED', 100,
                        '2026-08-31 23:59:59', 1, 1, 1, NULL, 501, CURRENT_TIMESTAMP, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_homework", Long.class);
    }

    private long insertSubmission(
            long homeworkId,
            long studentId,
            String submitType,
            String submitStatus,
            String evaluationStatus,
            String reviewStatus,
            int version,
            boolean isFinal,
            boolean deleted,
            String submittedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO t_hwk_submission
                        (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                         submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                         comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at,
                         is_deleted)
                        VALUES (?, ?, ?, 'answer', NULL, NULL, NULL, ?, ?, ?, NULL, NULL, NULL, NULL, ?, ?, ?,
                                NULL, NULL, ?, ?, ?)
                        """,
                homeworkId,
                studentId,
                submitType,
                submitStatus,
                evaluationStatus,
                reviewStatus,
                version,
                isFinal,
                submittedAt,
                submittedAt,
                submittedAt,
                deleted
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_submission", Long.class);
    }
}
