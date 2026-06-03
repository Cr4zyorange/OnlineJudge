package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkReviewLog;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Objects;

@Repository
public class JdbcHomeworkReviewLogRepository implements HomeworkReviewLogRepository {
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
}
