package com.onlinejudge.hwk.repository;

import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcHomeworkSubmissionRepository implements HomeworkSubmissionRepository {
    private static final RowMapper<HomeworkSubmission> ROW_MAPPER = (resultSet, rowNum) -> new HomeworkSubmission(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getLong("student_id"),
            resultSet.getString("submit_type"),
            resultSet.getString("answer_text"),
            resultSet.getString("answer_json"),
            resultSet.getString("file_url"),
            resultSet.getString("language"),
            resultSet.getString("submit_status"),
            resultSet.getString("evaluation_status"),
            resultSet.getString("review_status"),
            getInteger(resultSet, "auto_score"),
            getInteger(resultSet, "manual_score"),
            getInteger(resultSet, "final_score"),
            resultSet.getString("comment"),
            resultSet.getBoolean("is_final"),
            resultSet.getTimestamp("submitted_at").toLocalDateTime(),
            resultSet.getObject("reviewed_by", Long.class),
            toLocalDateTime(resultSet.getTimestamp("reviewed_at")),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHomeworkSubmissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HomeworkSubmission save(HomeworkSubmission submission) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_hwk_submission
                    (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                     submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                     comment, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, submission.homeworkId());
            statement.setLong(2, submission.studentId());
            statement.setString(3, submission.submitType());
            statement.setString(4, submission.answerText());
            statement.setString(5, submission.answerJson());
            statement.setString(6, submission.fileUrl());
            statement.setString(7, submission.language());
            statement.setString(8, submission.submitStatus());
            statement.setString(9, submission.evaluationStatus());
            statement.setString(10, submission.reviewStatus());
            statement.setObject(11, submission.autoScore());
            statement.setObject(12, submission.manualScore());
            statement.setObject(13, submission.finalScore());
            statement.setString(14, submission.comment());
            statement.setBoolean(15, submission.isFinal());
            statement.setTimestamp(16, Timestamp.valueOf(submission.submittedAt()));
            statement.setObject(17, submission.reviewedBy());
            statement.setObject(18, submission.reviewedAt() == null ? null : Timestamp.valueOf(submission.reviewedAt()));
            statement.setTimestamp(19, Timestamp.valueOf(submission.createdAt()));
            statement.setTimestamp(20, Timestamp.valueOf(submission.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("failed to reload saved submission"));
    }

    @Override
    public void clearFinalSubmission(long homeworkId, long studentId) {
        jdbcTemplate.update("""
                UPDATE t_hwk_submission
                SET is_final = FALSE, updated_at = CURRENT_TIMESTAMP
                WHERE homework_id = ? AND student_id = ? AND is_final = TRUE
                """, homeworkId, studentId);
    }

    @Override
    public Optional<HomeworkSubmission> findFinalByHomeworkAndStudent(long homeworkId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM t_hwk_submission
                        WHERE homework_id = ? AND student_id = ? AND is_final = TRUE
                        ORDER BY submitted_at DESC, id DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                homeworkId,
                studentId
        ).stream().findFirst();
    }

    @Override
    public List<HomeworkSubmission> findByHomeworkAndStudent(long homeworkId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM t_hwk_submission
                        WHERE homework_id = ? AND student_id = ?
                        ORDER BY submitted_at DESC, id DESC
                        """,
                ROW_MAPPER,
                homeworkId,
                studentId
        );
    }

    private Optional<HomeworkSubmission> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM t_hwk_submission WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    private static Integer getInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        java.math.BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? null : value.intValue();
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
