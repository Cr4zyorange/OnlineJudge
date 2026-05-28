package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkEvaluationStatus;
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
public class JdbcHomeworkEvaluationRepository implements HomeworkEvaluationRepository {
    private static final RowMapper<HomeworkEvaluation> ROW_MAPPER = (resultSet, rowNum) -> new HomeworkEvaluation(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getLong("submission_id"),
            resultSet.getString("evaluator_type"),
            HomeworkEvaluationStatus.valueOf(resultSet.getString("status")),
            resultSet.getBigDecimal("score"),
            resultSet.getBigDecimal("total_score"),
            resultSet.getInt("passed_count"),
            resultSet.getInt("total_count"),
            resultSet.getString("case_results_json"),
            resultSet.getString("message"),
            resultSet.getTimestamp("started_at").toLocalDateTime(),
            resultSet.getTimestamp("finished_at") == null ? null : resultSet.getTimestamp("finished_at").toLocalDateTime(),
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
                    (homework_id, submission_id, evaluator_type, status, score, total_score, passed_count,
                     total_count, case_results_json, message, started_at, finished_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, evaluation.homeworkId());
            statement.setLong(2, evaluation.submissionId());
            statement.setString(3, evaluation.evaluatorType());
            statement.setString(4, evaluation.status().name());
            statement.setBigDecimal(5, evaluation.score());
            statement.setBigDecimal(6, evaluation.totalScore());
            statement.setInt(7, evaluation.passedCount());
            statement.setInt(8, evaluation.totalCount());
            statement.setString(9, evaluation.caseResultsJson());
            statement.setString(10, evaluation.message());
            statement.setTimestamp(11, Timestamp.valueOf(evaluation.startedAt()));
            statement.setTimestamp(12, evaluation.finishedAt() == null ? null : Timestamp.valueOf(evaluation.finishedAt()));
            statement.setTimestamp(13, Timestamp.valueOf(evaluation.createdAt()));
            statement.setTimestamp(14, Timestamp.valueOf(evaluation.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("保存作业评测后无法读取记录"));
    }

    @Override
    public Optional<HomeworkEvaluation> findLatestBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, homework_id, submission_id, evaluator_type, status, score, total_score,
                               passed_count, total_count, case_results_json, message, started_at, finished_at,
                               created_at, updated_at
                        FROM t_hwk_evaluation
                        WHERE submission_id = ?
                        ORDER BY created_at DESC, id DESC
                        """,
                ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    private Optional<HomeworkEvaluation> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, homework_id, submission_id, evaluator_type, status, score, total_score,
                               passed_count, total_count, case_results_json, message, started_at, finished_at,
                               created_at, updated_at
                        FROM t_hwk_evaluation
                        WHERE id = ?
                        """,
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }
}
