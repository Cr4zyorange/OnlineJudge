package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkReviewLog;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

@Repository
public class JdbcHomeworkReviewLogRepository implements HomeworkReviewLogRepository {
    private static final RowMapper<HomeworkReviewLog> REVIEW_LOG_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkReviewLog(
            resultSet.getLong("id"),
            resultSet.getLong("submission_id"),
            resultSet.getLong("homework_id"),
            resultSet.getLong("student_id"),
            com.onlinejudge.hwk.domain.HomeworkReviewOperationType.valueOf(resultSet.getString("operation_type")),
            resultSet.getObject("old_score", BigDecimal.class),
            resultSet.getObject("new_score", BigDecimal.class),
            resultSet.getString("comment"),
            resultSet.getLong("operator_id"),
            resultSet.getString("reason"),
            resultSet.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkReviewLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HomeworkReviewLog save(HomeworkReviewLog reviewLog) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_hwk_review_log
                    (submission_id, homework_id, student_id, operation_type, old_score, new_score,
                     comment, operator_id, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, reviewLog.submissionId());
            statement.setLong(2, reviewLog.homeworkId());
            statement.setLong(3, reviewLog.studentId());
            statement.setString(4, reviewLog.operationType().name());
            statement.setObject(5, reviewLog.oldScore());
            statement.setObject(6, reviewLog.newScore());
            statement.setString(7, reviewLog.comment());
            statement.setLong(8, reviewLog.operatorId());
            statement.setString(9, reviewLog.reason());
            statement.setTimestamp(10, Timestamp.valueOf(reviewLog.createdAt()));
            return statement;
        }, keyHolder);
        return new HomeworkReviewLog(
                Objects.requireNonNull(keyHolder.getKey()).longValue(),
                reviewLog.submissionId(),
                reviewLog.homeworkId(),
                reviewLog.studentId(),
                reviewLog.operationType(),
                reviewLog.oldScore(),
                reviewLog.newScore(),
                reviewLog.comment(),
                reviewLog.operatorId(),
                reviewLog.reason(),
                reviewLog.createdAt()
        );
    }

    @Override
    public List<HomeworkReviewLog> findBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, submission_id, homework_id, student_id, operation_type, old_score, new_score,
                               comment, operator_id, reason, created_at
                        FROM t_hwk_review_log
                        WHERE submission_id = ?
                        ORDER BY created_at DESC, id DESC
                        """,
                REVIEW_LOG_ROW_MAPPER,
                submissionId
        );
    }
}
