package com.onlinejudge.lab.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.lab.domain.LabEvaluation;
import com.onlinejudge.lab.domain.LabEvaluationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcLabEvaluationRepository implements LabEvaluationRepository {
    private static final RowMapper<LabEvaluation> ROW_MAPPER = (resultSet, rowNum) -> new LabEvaluation(
            resultSet.getLong("id"),
            resultSet.getLong("submission_id"),
            EvaluationStatus.valueOf(resultSet.getString("status")),
            resultSet.getInt("score"),
            resultSet.getInt("passed_cases"),
            resultSet.getInt("total_cases"),
            resultSet.getObject("time_used_ms", Integer.class),
            resultSet.getObject("memory_used_kb", Integer.class),
            resultSet.getString("feedback"),
            resultSet.getString("compile_log"),
            resultSet.getString("run_log"),
            resultSet.getTimestamp("started_at").toLocalDateTime(),
            resultSet.getTimestamp("finished_at") == null ? null : resultSet.getTimestamp("finished_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabEvaluationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabEvaluation save(LabEvaluation evaluation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_evaluation
                    (submission_id, status, score, passed_cases, total_cases, time_used_ms, memory_used_kb,
                     feedback, compile_log, run_log, started_at, finished_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, evaluation.submissionId());
            statement.setString(2, evaluation.status().name());
            statement.setInt(3, evaluation.score());
            statement.setInt(4, evaluation.passedCases());
            statement.setInt(5, evaluation.totalCases());
            statement.setObject(6, evaluation.timeUsedMs());
            statement.setObject(7, evaluation.memoryUsedKb());
            statement.setString(8, evaluation.feedback());
            statement.setString(9, evaluation.compileLog());
            statement.setString(10, evaluation.runLog());
            statement.setTimestamp(11, Timestamp.valueOf(evaluation.startedAt()));
            statement.setTimestamp(12, evaluation.finishedAt() == null ? null : Timestamp.valueOf(evaluation.finishedAt()));
            statement.setTimestamp(13, Timestamp.valueOf(evaluation.createdAt()));
            statement.setTimestamp(14, Timestamp.valueOf(evaluation.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findLatestBySubmissionId(evaluation.submissionId()).orElseThrow();
    }

    @Override
    public LabEvaluation update(LabEvaluation evaluation) {
        jdbcTemplate.update("""
                UPDATE lab_evaluation
                SET status = ?, score = ?, passed_cases = ?, total_cases = ?, time_used_ms = ?, memory_used_kb = ?,
                    feedback = ?, compile_log = ?, run_log = ?, started_at = ?, finished_at = ?, updated_at = ?
                WHERE id = ?
                """,
                evaluation.status().name(),
                evaluation.score(),
                evaluation.passedCases(),
                evaluation.totalCases(),
                evaluation.timeUsedMs(),
                evaluation.memoryUsedKb(),
                evaluation.feedback(),
                evaluation.compileLog(),
                evaluation.runLog(),
                Timestamp.valueOf(evaluation.startedAt()),
                evaluation.finishedAt() == null ? null : Timestamp.valueOf(evaluation.finishedAt()),
                Timestamp.valueOf(evaluation.updatedAt()),
                evaluation.id()
        );
        return findLatestBySubmissionId(evaluation.submissionId()).orElseThrow();
    }

    @Override
    public Optional<LabEvaluation> findLatestBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, submission_id, status, score, passed_cases, total_cases, time_used_ms, memory_used_kb,
                               feedback, compile_log, run_log, started_at, finished_at, created_at, updated_at
                        FROM lab_evaluation
                        WHERE submission_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }
}
