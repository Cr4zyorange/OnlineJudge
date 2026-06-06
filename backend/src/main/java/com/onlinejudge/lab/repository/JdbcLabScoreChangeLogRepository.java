package com.onlinejudge.lab.repository;

import com.onlinejudge.lab.domain.LabScoreChangeLog;
import com.onlinejudge.lab.domain.LabScoreChangeLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Objects;

@Repository
public class JdbcLabScoreChangeLogRepository implements LabScoreChangeLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLabScoreChangeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabScoreChangeLog save(LabScoreChangeLog changeLog) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_score_change_log
                        (score_id, old_final_score, new_final_score, reason, operator_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, changeLog.scoreId());
            statement.setInt(2, changeLog.oldFinalScore());
            statement.setInt(3, changeLog.newFinalScore());
            statement.setString(4, changeLog.reason());
            statement.setLong(5, changeLog.operatorId());
            statement.setTimestamp(6, Timestamp.valueOf(changeLog.createdAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return new LabScoreChangeLog(
                id,
                changeLog.scoreId(),
                changeLog.oldFinalScore(),
                changeLog.newFinalScore(),
                changeLog.reason(),
                changeLog.operatorId(),
                changeLog.createdAt()
        );
    }

    @Override
    public int countByScoreId(long scoreId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_score_change_log WHERE score_id = ?",
                Integer.class,
                scoreId
        );
        return count == null ? 0 : count;
    }
}
