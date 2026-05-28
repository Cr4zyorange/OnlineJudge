package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkQuestionType;
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
import java.util.List;
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
            resultSet.getBigDecimal("total_score"),
            resultSet.getTimestamp("deadline").toLocalDateTime(),
            resultSet.getBoolean("allow_resubmit"),
            resultSet.getBoolean("allow_late_submit"),
            resultSet.getBoolean("show_evaluation_before_publish"),
            resultSet.getObject("judge_config_id", Long.class),
            resultSet.getLong("created_by"),
            resultSet.getTimestamp("published_at") == null ? null : resultSet.getTimestamp("published_at").toLocalDateTime(),
            resultSet.getBoolean("is_deleted"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(),
            List.of(),
            List.of()
    );

    private static final RowMapper<HomeworkQuestion> QUESTION_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkQuestion(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            HomeworkQuestionType.valueOf(resultSet.getString("question_type")),
            resultSet.getString("stem"),
            resultSet.getString("options_json"),
            resultSet.getString("answer_json"),
            resultSet.getBigDecimal("score"),
            resultSet.getInt("sort_order")
    );

    private static final RowMapper<HomeworkTestCase> TEST_CASE_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkTestCase(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getString("input_data"),
            resultSet.getString("expected_output"),
            resultSet.getBigDecimal("score_weight"),
            resultSet.getBoolean("is_hidden"),
            resultSet.getInt("time_limit_ms"),
            resultSet.getInt("memory_limit_kb"),
            resultSet.getInt("sort_order")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
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
            bindHomework(statement, homework);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        saveChildren(id, homework);
        return findById(id).orElseThrow(() -> new IllegalStateException("保存作业后无法读取记录"));
    }

    @Override
    @Transactional
    public Homework update(Homework homework) {
        int updated = jdbcTemplate.update("""
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
                    judge_config_id = ?,
                    published_at = ?,
                    is_deleted = ?,
                    updated_at = ?
                WHERE id = ?
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
                homework.judgeConfigId(),
                homework.publishedAt() == null ? null : Timestamp.valueOf(homework.publishedAt()),
                homework.deleted(),
                Timestamp.valueOf(homework.updatedAt()),
                homework.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("作业不存在");
        }
        replaceChildren(homework);
        return findById(homework.id()).orElseThrow(() -> new IllegalStateException("更新作业后无法读取记录"));
    }

    @Override
    public Optional<Homework> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, chapter_id, title, description, type, status, total_score, deadline,
                               allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                               created_by, published_at, is_deleted, created_at, updated_at
                        FROM t_hwk_homework
                        WHERE id = ?
                        """,
                HOMEWORK_ROW_MAPPER,
                id
        ).stream().findFirst().map(this::attachChildren);
    }

    @Override
    public List<Homework> findByCourseId(long courseId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, chapter_id, title, description, type, status, total_score, deadline,
                               allow_resubmit, allow_late_submit, show_evaluation_before_publish, judge_config_id,
                               created_by, published_at, is_deleted, created_at, updated_at
                        FROM t_hwk_homework
                        WHERE course_id = ? AND is_deleted = FALSE
                        ORDER BY deadline ASC, id ASC
                        """,
                HOMEWORK_ROW_MAPPER,
                courseId
        ).stream().map(this::attachChildren).toList();
    }

    private Homework attachChildren(Homework homework) {
        return homework.withChildren(findQuestions(homework.id()), findTestCases(homework.id()));
    }

    private List<HomeworkQuestion> findQuestions(long homeworkId) {
        return jdbcTemplate.query("""
                        SELECT id, homework_id, question_type, stem, options_json, answer_json, score, sort_order
                        FROM t_hwk_question
                        WHERE homework_id = ?
                        ORDER BY sort_order ASC, id ASC
                        """,
                QUESTION_ROW_MAPPER,
                homeworkId
        );
    }

    private List<HomeworkTestCase> findTestCases(long homeworkId) {
        return jdbcTemplate.query("""
                        SELECT id, homework_id, input_data, expected_output, score_weight, is_hidden,
                               time_limit_ms, memory_limit_kb, sort_order
                        FROM t_hwk_test_case
                        WHERE homework_id = ?
                        ORDER BY sort_order ASC, id ASC
                        """,
                TEST_CASE_ROW_MAPPER,
                homeworkId
        );
    }

    private void replaceChildren(Homework homework) {
        jdbcTemplate.update("DELETE FROM t_hwk_question WHERE homework_id = ?", homework.id());
        jdbcTemplate.update("DELETE FROM t_hwk_test_case WHERE homework_id = ?", homework.id());
        saveChildren(homework.id(), homework);
    }

    private void saveChildren(long homeworkId, Homework homework) {
        for (HomeworkQuestion question : homework.questions()) {
            HomeworkQuestion item = question.withHomeworkId(homeworkId);
            jdbcTemplate.update("""
                    INSERT INTO t_hwk_question
                    (homework_id, question_type, stem, options_json, answer_json, score, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.homeworkId(),
                    item.questionType().name(),
                    item.stem(),
                    item.optionsJson(),
                    item.answerJson(),
                    item.score(),
                    item.sortOrder()
            );
        }
        for (HomeworkTestCase testCase : homework.testCases()) {
            HomeworkTestCase item = testCase.withHomeworkId(homeworkId);
            jdbcTemplate.update("""
                    INSERT INTO t_hwk_test_case
                    (homework_id, input_data, expected_output, score_weight, is_hidden,
                     time_limit_ms, memory_limit_kb, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.homeworkId(),
                    item.inputData(),
                    item.expectedOutput(),
                    item.scoreWeight(),
                    item.hidden(),
                    item.timeLimitMs(),
                    item.memoryLimitKb(),
                    item.sortOrder()
            );
        }
    }

    private void bindHomework(PreparedStatement statement, Homework homework) throws java.sql.SQLException {
        statement.setLong(1, homework.courseId());
        if (homework.chapterId() == null) {
            statement.setObject(2, null);
        } else {
            statement.setLong(2, homework.chapterId());
        }
        statement.setString(3, homework.title());
        statement.setString(4, homework.description());
        statement.setString(5, homework.type().name());
        statement.setString(6, homework.status().name());
        statement.setBigDecimal(7, homework.totalScore());
        statement.setTimestamp(8, Timestamp.valueOf(homework.deadline()));
        statement.setBoolean(9, homework.allowResubmit());
        statement.setBoolean(10, homework.allowLateSubmit());
        statement.setBoolean(11, homework.showEvaluationBeforePublish());
        if (homework.judgeConfigId() == null) {
            statement.setObject(12, null);
        } else {
            statement.setLong(12, homework.judgeConfigId());
        }
        statement.setLong(13, homework.createdBy());
        if (homework.publishedAt() == null) {
            statement.setObject(14, null);
        } else {
            statement.setTimestamp(14, Timestamp.valueOf(homework.publishedAt()));
        }
        statement.setBoolean(15, homework.deleted());
        statement.setTimestamp(16, Timestamp.valueOf(homework.createdAt()));
        statement.setTimestamp(17, Timestamp.valueOf(homework.updatedAt()));
    }
}
