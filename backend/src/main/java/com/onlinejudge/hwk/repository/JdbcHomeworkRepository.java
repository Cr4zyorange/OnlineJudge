package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkJudgeConfig;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcHomeworkRepository implements HomeworkRepository {
    private static final RowMapper<Homework> HOMEWORK_ROW_MAPPER = (resultSet, rowNum) -> new Homework(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getObject("chapter_id", Long.class),
            resultSet.getString("title"),
            resultSet.getString("description"),
            HomeworkType.valueOf(resultSet.getString("type")),
            HomeworkStatus.valueOf(resultSet.getString("status")),
            resultSet.getBigDecimal("total_score").intValue(),
            resultSet.getTimestamp("deadline").toLocalDateTime(),
            resultSet.getBoolean("allow_resubmit"),
            resultSet.getBoolean("allow_late_submit"),
            resultSet.getBoolean("show_evaluation_before_publish"),
            resultSet.getObject("judge_config_id", Long.class),
            resultSet.getLong("created_by"),
            toLocalDateTime(resultSet.getTimestamp("published_at")),
            resultSet.getBoolean("is_deleted"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(),
            List.of(),
            List.of(),
            null
    );

    private static final RowMapper<HomeworkQuestion> QUESTION_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkQuestion(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getString("question_type"),
            resultSet.getString("stem"),
            resultSet.getString("options_json"),
            resultSet.getString("answer_json"),
            resultSet.getBigDecimal("score").intValue(),
            resultSet.getInt("sort_order"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<HomeworkTestCase> TEST_CASE_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkTestCase(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getString("input_data"),
            resultSet.getString("expected_output"),
            resultSet.getBigDecimal("score_weight").intValue(),
            resultSet.getBoolean("is_hidden"),
            resultSet.getInt("time_limit_ms"),
            resultSet.getInt("memory_limit_kb"),
            resultSet.getInt("sort_order"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<HomeworkJudgeConfig> JUDGE_CONFIG_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkJudgeConfig(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getString("language_limit_json"),
            resultSet.getInt("time_limit_ms"),
            resultSet.getInt("memory_limit_kb"),
            resultSet.getString("output_compare_mode"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Homework save(Homework homework) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_hwk_homework
                    (course_id, chapter_id, title, description, type, status, total_score, deadline,
                     allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                     created_by, published_at, is_deleted, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bindHomework(statement, homework, null);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        replaceQuestionRows(id, homework.questions());
        replaceTestCaseRows(id, homework.testCases());
        replaceJudgeConfig(id, homework.judgeConfig(), homework.createdAt());
        return findById(id).orElseThrow(() -> new IllegalStateException("failed to reload saved homework"));
    }

    @Override
    @Transactional
    public Optional<Homework> update(Homework homework) {
        int affectedRows = jdbcTemplate.update("""
                UPDATE t_hwk_homework
                SET chapter_id = ?,
                    title = ?,
                    description = ?,
                    type = ?,
                    status = ?,
                    total_score = ?,
                    deadline = ?,
                    allow_resubmit = ?,
                    allow_late_submit = ?,
                    show_evaluation_before_publish = ?,
                    published_at = ?,
                    updated_at = ?
                WHERE id = ? AND is_deleted = FALSE
                """,
                homework.chapterId(),
                homework.title(),
                homework.description(),
                homework.type().name(),
                homework.status().name(),
                homework.totalScore(),
                Timestamp.valueOf(homework.deadline()),
                homework.allowResubmit(),
                homework.allowLateSubmit(),
                homework.showEvaluationBeforePublish(),
                homework.publishedAt() == null ? null : Timestamp.valueOf(homework.publishedAt()),
                Timestamp.valueOf(homework.updatedAt()),
                homework.id()
        );
        if (affectedRows == 0) {
            return Optional.empty();
        }
        replaceJudgeConfig(homework.id(), homework.judgeConfig(), homework.updatedAt());
        replaceQuestionRows(homework.id(), homework.questions());
        replaceTestCaseRows(homework.id(), homework.testCases());
        return findById(homework.id()).filter(updated -> !updated.deleted());
    }

    @Override
    @Transactional
    public Optional<Homework> replaceQuestions(long homeworkId, List<HomeworkQuestion> questions) {
        LocalDateTime updatedAt = LocalDateTime.now();
        if (!touchActive(homeworkId, updatedAt)) {
            return Optional.empty();
        }
        replaceQuestionRows(homeworkId, questions);
        return findById(homeworkId).filter(updated -> !updated.deleted());
    }

    @Override
    @Transactional
    public Optional<Homework> replaceTestCases(long homeworkId, List<HomeworkTestCase> testCases) {
        LocalDateTime updatedAt = LocalDateTime.now();
        if (!touchActive(homeworkId, updatedAt)) {
            return Optional.empty();
        }
        replaceTestCaseRows(homeworkId, testCases);
        return findById(homeworkId).filter(updated -> !updated.deleted());
    }

    @Override
    @Transactional
    public boolean softDeleteDraft(long homeworkId, LocalDateTime deletedAt) {
        return jdbcTemplate.update("""
                        UPDATE t_hwk_homework
                        SET is_deleted = TRUE, updated_at = ?
                        WHERE id = ? AND status = 'DRAFT' AND is_deleted = FALSE
                        """,
                Timestamp.valueOf(deletedAt),
                homeworkId
        ) == 1;
    }

    @Override
    public Optional<Homework> findById(long homeworkId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, chapter_id, title, description, type, status, total_score, deadline,
                               allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                               created_by, published_at, is_deleted, created_at, updated_at
                        FROM t_hwk_homework
                        WHERE id = ?
                        """,
                HOMEWORK_ROW_MAPPER,
                homeworkId
        ).stream().findFirst().map(this::attachChildren);
    }

    @Override
    public Optional<Homework> findByIdForUpdate(long homeworkId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, chapter_id, title, description, type, status, total_score, deadline,
                               allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                               created_by, published_at, is_deleted, created_at, updated_at
                        FROM t_hwk_homework
                        WHERE id = ?
                        FOR UPDATE
                        """,
                HOMEWORK_ROW_MAPPER,
                homeworkId
        ).stream().findFirst().map(this::attachChildren);
    }

    @Override
    public List<Homework> findByCourseId(long courseId, HomeworkStatus status, String keyword, int page, int size) {
        String sql = """
                SELECT id, course_id, chapter_id, title, description, type, status, total_score, deadline,
                       allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                       created_by, published_at, is_deleted, created_at, updated_at
                FROM t_hwk_homework
                WHERE course_id = ? AND is_deleted = FALSE
                """;
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        if (status != null) {
            sql += " AND status = ? ";
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND LOWER(title) LIKE ? ";
            args.add("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
        }
        sql += " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ? ";
        args.add(size);
        args.add((Math.max(page, 1) - 1) * size);
        return jdbcTemplate.query(sql, HOMEWORK_ROW_MAPPER, args.toArray()).stream()
                .map(this::attachChildren)
                .toList();
    }

    @Override
    public long countByCourseId(long courseId, HomeworkStatus status, String keyword) {
        String sql = "SELECT COUNT(*) FROM t_hwk_homework WHERE course_id = ? AND is_deleted = FALSE";
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        if (status != null) {
            sql += " AND status = ? ";
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND LOWER(title) LIKE ? ";
            args.add("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public List<Homework> findByCourseIdAndStatuses(
            long courseId,
            List<HomeworkStatus> statuses,
            String keyword,
            int page,
            int size
    ) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id, course_id, chapter_id, title, description, type, status, total_score, deadline,
                       allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                       created_by, published_at, is_deleted, created_at, updated_at
                FROM t_hwk_homework
                WHERE course_id = ? AND is_deleted = FALSE
                  AND status IN (%s)
                """.formatted(placeholders(statuses.size()));
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        statuses.stream().map(HomeworkStatus::name).forEach(args::add);
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND LOWER(title) LIKE ? ";
            args.add("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
        }
        sql += " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ? ";
        args.add(size);
        args.add((Math.max(page, 1) - 1) * size);
        return jdbcTemplate.query(sql, HOMEWORK_ROW_MAPPER, args.toArray()).stream()
                .map(this::attachChildren)
                .toList();
    }

    @Override
    public long countByCourseIdAndStatuses(long courseId, List<HomeworkStatus> statuses, String keyword) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }
        String sql = """
                SELECT COUNT(*)
                FROM t_hwk_homework
                WHERE course_id = ? AND is_deleted = FALSE
                  AND status IN (%s)
                """.formatted(placeholders(statuses.size()));
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        statuses.stream().map(HomeworkStatus::name).forEach(args::add);
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND LOWER(title) LIKE ? ";
            args.add("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private Homework attachChildren(Homework homework) {
        List<HomeworkQuestion> questions = jdbcTemplate.query("""
                        SELECT id, homework_id, question_type, stem, options_json, answer_json, score,
                               sort_order, created_at, updated_at
                        FROM t_hwk_question
                        WHERE homework_id = ?
                        ORDER BY sort_order ASC, id ASC
                        """,
                QUESTION_ROW_MAPPER,
                homework.id()
        );
        List<HomeworkTestCase> testCases = jdbcTemplate.query("""
                        SELECT id, homework_id, input_data, expected_output, score_weight, is_hidden,
                               time_limit_ms, memory_limit_kb, sort_order, created_at, updated_at
                        FROM t_hwk_test_case
                        WHERE homework_id = ?
                        ORDER BY sort_order ASC, id ASC
                        """,
                TEST_CASE_ROW_MAPPER,
                homework.id()
        );
        HomeworkJudgeConfig judgeConfig = jdbcTemplate.query("""
                        SELECT id, homework_id, language_limit_json, time_limit_ms, memory_limit_kb,
                               output_compare_mode, created_at, updated_at
                        FROM t_hwk_judge_config
                        WHERE homework_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                JUDGE_CONFIG_ROW_MAPPER,
                homework.id()
        ).stream().findFirst().orElse(null);
        Long judgeConfigId = judgeConfig == null ? null : judgeConfig.id();
        return new Homework(homework.id(), homework.courseId(), homework.chapterId(), homework.title(),
                homework.description(), homework.type(), homework.status(), homework.totalScore(), homework.deadline(),
                homework.allowResubmit(), homework.allowLateSubmit(), homework.showEvaluationBeforePublish(),
                judgeConfigId, homework.createdBy(), homework.publishedAt(), homework.deleted(), homework.createdAt(),
                homework.updatedAt(), questions, testCases, judgeConfig);
    }

    private void replaceQuestionRows(long homeworkId, List<HomeworkQuestion> questions) {
        jdbcTemplate.update("DELETE FROM t_hwk_question WHERE homework_id = ?", homeworkId);
        LocalDateTime now = LocalDateTime.now();
        for (HomeworkQuestion question : questions == null ? List.<HomeworkQuestion>of() : questions) {
            jdbcTemplate.update("""
                            INSERT INTO t_hwk_question
                            (homework_id, question_type, stem, options_json, answer_json, score, sort_order, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    homeworkId,
                    question.questionType(),
                    question.stem(),
                    question.optionsJson(),
                    question.answerJson(),
                    question.score(),
                    question.sortOrder(),
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            );
        }
    }

    private void replaceTestCaseRows(long homeworkId, List<HomeworkTestCase> testCases) {
        jdbcTemplate.update("DELETE FROM t_hwk_test_case WHERE homework_id = ?", homeworkId);
        LocalDateTime now = LocalDateTime.now();
        for (HomeworkTestCase testCase : testCases == null ? List.<HomeworkTestCase>of() : testCases) {
            jdbcTemplate.update("""
                            INSERT INTO t_hwk_test_case
                            (homework_id, input_data, expected_output, score_weight, is_hidden, time_limit_ms,
                             memory_limit_kb, sort_order, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    homeworkId,
                    testCase.inputData(),
                    testCase.expectedOutput(),
                    testCase.scoreWeight(),
                    testCase.hidden(),
                    testCase.timeLimitMs(),
                    testCase.memoryLimitKb(),
                    testCase.sortOrder(),
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            );
        }
    }

    private Long replaceJudgeConfig(long homeworkId, HomeworkJudgeConfig judgeConfig, LocalDateTime now) {
        jdbcTemplate.update("DELETE FROM t_hwk_judge_config WHERE homework_id = ?", homeworkId);
        if (judgeConfig == null) {
            jdbcTemplate.update(
                    "UPDATE t_hwk_homework SET judge_config_id = NULL WHERE id = ? AND is_deleted = FALSE",
                    homeworkId
            );
            return null;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_hwk_judge_config
                    (homework_id, language_limit_json, time_limit_ms, memory_limit_kb, output_compare_mode, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, homeworkId);
            statement.setString(2, judgeConfig.languageLimitJson());
            statement.setInt(3, judgeConfig.timeLimitMs());
            statement.setInt(4, judgeConfig.memoryLimitKb());
            statement.setString(5, judgeConfig.outputCompareMode());
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setTimestamp(7, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        long configId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        jdbcTemplate.update(
                "UPDATE t_hwk_homework SET judge_config_id = ? WHERE id = ? AND is_deleted = FALSE",
                configId,
                homeworkId
        );
        return configId;
    }

    private boolean touchActive(long homeworkId, LocalDateTime updatedAt) {
        return jdbcTemplate.update(
                "UPDATE t_hwk_homework SET updated_at = ? WHERE id = ? AND is_deleted = FALSE",
                Timestamp.valueOf(updatedAt),
                homeworkId
        ) == 1;
    }

    private void bindHomework(PreparedStatement statement, Homework homework, Long judgeConfigId) throws java.sql.SQLException {
        statement.setLong(1, homework.courseId());
        statement.setObject(2, homework.chapterId());
        statement.setString(3, homework.title());
        statement.setString(4, homework.description());
        statement.setString(5, homework.type().name());
        statement.setString(6, homework.status().name());
        statement.setInt(7, homework.totalScore());
        statement.setTimestamp(8, Timestamp.valueOf(homework.deadline()));
        statement.setBoolean(9, homework.allowResubmit());
        statement.setBoolean(10, homework.allowLateSubmit());
        statement.setBoolean(11, homework.showEvaluationBeforePublish());
        statement.setObject(12, judgeConfigId);
        statement.setLong(13, homework.createdBy());
        statement.setObject(14, homework.publishedAt() == null ? null : Timestamp.valueOf(homework.publishedAt()));
        statement.setBoolean(15, homework.deleted());
        statement.setTimestamp(16, Timestamp.valueOf(homework.createdAt()));
        statement.setTimestamp(17, Timestamp.valueOf(homework.updatedAt()));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
