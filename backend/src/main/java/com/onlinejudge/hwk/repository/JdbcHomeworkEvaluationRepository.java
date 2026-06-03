package com.onlinejudge.hwk.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
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
    private static final RowMapper<HomeworkEvaluation> ROW_MAPPER = (resultSet, rowNum) -> new HomeworkEvaluation(
            resultSet.getLong("id"),
            resultSet.getLong("submission_id"),
            EvaluationStatus.valueOf(resultSet.getString("status")),
            resultSet.getBigDecimal("score").intValue(),
            resultSet.getInt("passed_cases"),
            resultSet.getInt("total_cases"),
            resultSet.getObject("duration_ms", Integer.class),
            resultSet.getString("error_message"),
            resultSet.getString("feedback"),
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
                    (submission_id, status, score, passed_cases, total_cases, duration_ms, error_message,
                     feedback, compile_log, run_log, reevaluation, triggered_by, started_at, finished_at,
                     created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bind(statement, evaluation);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("failed to reload homework evaluation"));
    }

    @Override
    public HomeworkEvaluation update(HomeworkEvaluation evaluation) {
        int updated = jdbcTemplate.update("""
                UPDATE t_hwk_evaluation
                SET status = ?,
                    score = ?,
                    passed_cases = ?,
                    total_cases = ?,
                    duration_ms = ?,
                    error_message = ?,
                    feedback = ?,
                    compile_log = ?,
                    run_log = ?,
                    reevaluation = ?,
                    triggered_by = ?,
                    started_at = ?,
                    finished_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                evaluation.status().name(),
                evaluation.score(),
                evaluation.passedCases(),
                evaluation.totalCases(),
                evaluation.durationMs(),
                evaluation.errorMessage(),
                evaluation.feedback(),
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
                        SELECT id, submission_id, status, score, passed_cases, total_cases, duration_ms,
                               error_message, feedback, compile_log, run_log, reevaluation, triggered_by,
                               started_at, finished_at, created_at, updated_at
                        FROM t_hwk_evaluation
                        WHERE submission_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    @Override
    public Optional<HomeworkEvaluation> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, submission_id, status, score, passed_cases, total_cases, duration_ms,
                               error_message, feedback, compile_log, run_log, reevaluation, triggered_by,
                               started_at, finished_at, created_at, updated_at
                        FROM t_hwk_evaluation
                        WHERE id = ?
                        """,
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    private static void bind(PreparedStatement statement, HomeworkEvaluation evaluation) throws java.sql.SQLException {
        statement.setLong(1, evaluation.submissionId());
        statement.setString(2, evaluation.status().name());
        statement.setInt(3, evaluation.score());
        statement.setInt(4, evaluation.passedCases());
        statement.setInt(5, evaluation.totalCases());
        statement.setObject(6, evaluation.durationMs());
        statement.setString(7, evaluation.errorMessage());
        statement.setString(8, evaluation.feedback());
        statement.setString(9, evaluation.compileLog());
        statement.setString(10, evaluation.runLog());
        statement.setBoolean(11, evaluation.reevaluation());
        statement.setObject(12, evaluation.triggeredBy());
        statement.setTimestamp(13, Timestamp.valueOf(evaluation.startedAt()));
        statement.setObject(14, evaluation.finishedAt() == null ? null : Timestamp.valueOf(evaluation.finishedAt()));
        statement.setTimestamp(15, Timestamp.valueOf(evaluation.createdAt()));
        statement.setTimestamp(16, Timestamp.valueOf(evaluation.updatedAt()));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
