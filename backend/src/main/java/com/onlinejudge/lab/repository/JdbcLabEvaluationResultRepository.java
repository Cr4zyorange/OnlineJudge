package com.onlinejudge.lab.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.lab.domain.LabEvaluationCaseResult;
import com.onlinejudge.lab.domain.LabEvaluationResultRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcLabEvaluationResultRepository implements LabEvaluationResultRepository {
    private static final RowMapper<LabEvaluationCaseResult> ROW_MAPPER = (resultSet, rowNum) -> new LabEvaluationCaseResult(
            resultSet.getLong("id"),
            resultSet.getLong("submission_id"),
            resultSet.getLong("testcase_id"),
            resultSet.getInt("order_num"),
            resultSet.getBoolean("is_public"),
            EvaluationStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("passed"),
            resultSet.getInt("score"),
            resultSet.getString("input"),
            resultSet.getString("expected_output"),
            resultSet.getString("actual_output"),
            resultSet.getString("message"),
            resultSet.getTimestamp("executed_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabEvaluationResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replaceSubmissionResults(long submissionId, List<LabEvaluationCaseResult> results) {
        jdbcTemplate.update("DELETE FROM lab_evaluation_result WHERE submission_id = ?", submissionId);
        for (LabEvaluationCaseResult result : results) {
            jdbcTemplate.update("""
                            INSERT INTO lab_evaluation_result
                            (submission_id, testcase_id, status, passed, score, actual_output, message, executed_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    submissionId,
                    result.testcaseId(),
                    result.status().name(),
                    result.passed(),
                    result.score(),
                    result.actualOutput(),
                    result.message(),
                    Timestamp.valueOf(result.executedAt()),
                    Timestamp.valueOf(result.createdAt()),
                    Timestamp.valueOf(result.updatedAt())
            );
        }
    }

    @Override
    public List<LabEvaluationCaseResult> findBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT r.id, r.submission_id, r.testcase_id, t.order_num, r.status, r.passed, r.score,
                               t.is_public, t.input, t.expected_output, r.actual_output, r.message, r.executed_at, r.created_at, r.updated_at
                        FROM lab_evaluation_result r
                        INNER JOIN lab_testcase t ON t.id = r.testcase_id
                        WHERE r.submission_id = ?
                        ORDER BY t.order_num ASC, r.id ASC
                        """,
                ROW_MAPPER,
                submissionId
        );
    }
}
