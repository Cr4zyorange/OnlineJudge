package com.onlinejudge.lab.repository;

import com.onlinejudge.lab.domain.LabScore;
import com.onlinejudge.lab.domain.LabScoreRepository;
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
public class JdbcLabScoreRepository implements LabScoreRepository {
    private static final RowMapper<LabScore> ROW_MAPPER = (resultSet, rowNum) -> new LabScore(
            resultSet.getLong("id"),
            resultSet.getLong("submission_id"),
            resultSet.getObject("report_id", Long.class),
            resultSet.getLong("teacher_id"),
            resultSet.getObject("auto_score", Integer.class),
            resultSet.getObject("report_score", Integer.class),
            resultSet.getObject("manual_score", Integer.class),
            resultSet.getInt("final_score"),
            resultSet.getString("comment"),
            resultSet.getTimestamp("scored_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabScoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabScore save(LabScore score) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_score
                        (submission_id, report_id, teacher_id, auto_score, report_score, manual_score, final_score, comment, scored_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, score.submissionId());
            statement.setObject(2, score.reportId());
            statement.setLong(3, score.teacherId());
            statement.setObject(4, score.autoScore());
            statement.setObject(5, score.reportScore());
            statement.setObject(6, score.manualScore());
            statement.setInt(7, score.finalScore());
            statement.setString(8, score.comment());
            statement.setTimestamp(9, Timestamp.valueOf(score.scoredAt()));
            statement.setTimestamp(10, Timestamp.valueOf(score.updatedAt()));
            return statement;
        }, keyHolder);
        return findBySubmissionId(score.submissionId())
                .orElseThrow(() -> new IllegalStateException("保存实验评分后无法读取记录"));
    }

    @Override
    public LabScore update(LabScore score) {
        int updated = jdbcTemplate.update("""
                UPDATE lab_score
                SET report_id = ?,
                    teacher_id = ?,
                    auto_score = ?,
                    report_score = ?,
                    manual_score = ?,
                    final_score = ?,
                    comment = ?,
                    scored_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                score.reportId(),
                score.teacherId(),
                score.autoScore(),
                score.reportScore(),
                score.manualScore(),
                score.finalScore(),
                score.comment(),
                Timestamp.valueOf(score.scoredAt()),
                Timestamp.valueOf(score.updatedAt()),
                score.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("实验评分记录不存在");
        }
        return findBySubmissionId(score.submissionId())
                .orElseThrow(() -> new IllegalStateException("更新实验评分后无法读取记录"));
    }

    @Override
    public Optional<LabScore> findBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, submission_id, report_id, teacher_id, auto_score, report_score, manual_score,
                               final_score, comment, scored_at, updated_at
                        FROM lab_score
                        WHERE submission_id = ?
                        """,
                ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }
}
