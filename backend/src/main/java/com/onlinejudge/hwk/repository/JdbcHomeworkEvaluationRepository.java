package com.onlinejudge.hwk.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkEvaluationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcHomeworkEvaluationRepository implements HomeworkEvaluationRepository {
    private static final String SELECT_COLUMNS = """
            id, submission_id, homework_id, student_id, evaluation_type, status, score,
            passed_cases, total_cases, time_used_ms, memory_used_kb, error_message, feedback,
            log_url, compile_log, run_log, reevaluation, triggered_by, started_at, finished_at,
            created_at, updated_at
            """;

    private static final RowMapper<HomeworkEvaluation> ROW_MAPPER = (resultSet, rowNum) -> new HomeworkEvaluation(
            resultSet.getLong("id"),
            resultSet.getLong("submission_id"),
            resultSet.getLong("homework_id"),
            resultSet.getLong("student_id"),
            HomeworkEvaluationType.valueOf(resultSet.getString("evaluation_type")),
            EvaluationStatus.valueOf(resultSet.getString("status")),
            resultSet.getBigDecimal("score").intValue(),
            resultSet.getInt("passed_cases"),
            resultSet.getInt("total_cases"),
            resultSet.getObject("time_used_ms", Integer.class),
            resultSet.getObject("memory_used_kb", Integer.class),
            resultSet.getString("error_message"),
            resultSet.getString("feedback"),
            resultSet.getString("log_url"),
            resultSet.getString("compile_log"),
            resultSet.getString("run_log"),
            resultSet.getBoolean("reevaluation"),
            resultSet.getObject("triggered_by", Long.class),
            resultSet.getTimestamp("started_at").toLocalDateTime(),
            toLocalDateTime(resultSet.getTimestamp("finished_at")),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkEvaluationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HomeworkEvaluation save(HomeworkEvaluation evaluation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_hwk_evaluation
                    (submission_id, homework_id, student_id, evaluation_type, status, score,
                     passed_cases, total_cases, time_used_ms, memory_used_kb, error_message, feedback,
                     log_url, compile_log, run_log, reevaluation, triggered_by, started_at, finished_at,
                     created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bindForInsert(statement, evaluation);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("failed to reload homework evaluation"));
    }

    @Override
    public HomeworkEvaluation update(HomeworkEvaluation evaluation) {
        int updated = jdbcTemplate.update("""
                UPDATE t_hwk_evaluation
                SET homework_id = ?,
                    student_id = ?,
                    evaluation_type = ?,
                    status = ?,
                    score = ?,
                    passed_cases = ?,
                    total_cases = ?,
                    time_used_ms = ?,
                    memory_used_kb = ?,
                    error_message = ?,
                    feedback = ?,
                    log_url = ?,
                    compile_log = ?,
                    run_log = ?,
                    reevaluation = ?,
                    triggered_by = ?,
                    started_at = ?,
                    finished_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                evaluation.homeworkId(),
                evaluation.studentId(),
                evaluation.evaluationType().name(),
                evaluation.status().name(),
                evaluation.score(),
                evaluation.passedCases(),
                evaluation.totalCases(),
                evaluation.timeUsedMs(),
                evaluation.memoryUsedKb(),
                evaluation.errorMessage(),
                evaluation.feedback(),
                evaluation.logUrl(),
                evaluation.compileLog(),
                evaluation.runLog(),
                evaluation.reevaluation(),
                evaluation.triggeredBy(),
                Timestamp.valueOf(evaluation.startedAt()),
                evaluation.finishedAt() == null ? null : Timestamp.valueOf(evaluation.finishedAt()),
                Timestamp.valueOf(evaluation.updatedAt()),
                evaluation.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("homework evaluation not found");
        }
        return findById(evaluation.id()).orElseThrow(() -> new IllegalStateException("failed to reload homework evaluation"));
    }

    @Override
    public Optional<HomeworkEvaluation> findLatestBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM t_hwk_evaluation
                        WHERE submission_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """.formatted(SELECT_COLUMNS),
                ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    @Override
    public Optional<HomeworkEvaluation> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM t_hwk_evaluation
                        WHERE id = ?
                        """.formatted(SELECT_COLUMNS),
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    private static void bindForInsert(PreparedStatement statement, HomeworkEvaluation evaluation) throws java.sql.SQLException {
        statement.setLong(1, evaluation.submissionId());
        statement.setLong(2, evaluation.homeworkId());
        statement.setLong(3, evaluation.studentId());
        statement.setString(4, evaluation.evaluationType().name());
        statement.setString(5, evaluation.status().name());
        statement.setInt(6, evaluation.score());
        statement.setInt(7, evaluation.passedCases());
        statement.setInt(8, evaluation.totalCases());
        statement.setObject(9, evaluation.timeUsedMs());
        statement.setObject(10, evaluation.memoryUsedKb());
        statement.setString(11, evaluation.errorMessage());
        statement.setString(12, evaluation.feedback());
        statement.setString(13, evaluation.logUrl());
        statement.setString(14, evaluation.compileLog());
        statement.setString(15, evaluation.runLog());
        statement.setBoolean(16, evaluation.reevaluation());
        statement.setObject(17, evaluation.triggeredBy());
        statement.setTimestamp(18, Timestamp.valueOf(evaluation.startedAt()));
        statement.setObject(19, evaluation.finishedAt() == null ? null : Timestamp.valueOf(evaluation.finishedAt()));
        statement.setTimestamp(20, Timestamp.valueOf(evaluation.createdAt()));
        statement.setTimestamp(21, Timestamp.valueOf(evaluation.updatedAt()));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
