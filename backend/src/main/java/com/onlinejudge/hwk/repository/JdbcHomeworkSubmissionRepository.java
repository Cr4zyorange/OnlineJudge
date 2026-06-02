package com.onlinejudge.hwk.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkType;
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
public class JdbcHomeworkSubmissionRepository implements HomeworkSubmissionRepository {
    private static final RowMapper<HomeworkSubmission> SUBMISSION_ROW_MAPPER = (resultSet, rowNum) -> new HomeworkSubmission(
            resultSet.getLong("id"),
            resultSet.getLong("homework_id"),
            resultSet.getLong("student_id"),
            HomeworkType.valueOf(resultSet.getString("submit_type")),
            resultSet.getString("answer_text"),
            resultSet.getString("answer_json"),
            resultSet.getString("file_url"),
            resultSet.getString("language"),
            HomeworkSubmitStatus.valueOf(resultSet.getString("submit_status")),
            EvaluationStatus.valueOf(resultSet.getString("evaluation_status")),
            HomeworkReviewStatus.valueOf(resultSet.getString("review_status")),
            resultSet.getObject("auto_score", Integer.class),
            resultSet.getObject("manual_score", Integer.class),
            resultSet.getObject("final_score", Integer.class),
            resultSet.getString("comment"),
            resultSet.getInt("version"),
            resultSet.getBoolean("is_final"),
            resultSet.getTimestamp("submitted_at").toLocalDateTime(),
            resultSet.getObject("reviewed_by", Long.class),
            toLocalDateTime(resultSet.getTimestamp("reviewed_at")),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(),
            resultSet.getBoolean("is_deleted")
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
                     submit_status, evaluation_status, review_status, auto_score, manual_score, final_score, comment,
                     version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at, is_deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, submission.homeworkId());
            statement.setLong(2, submission.studentId());
            statement.setString(3, submission.submitType().name());
            statement.setString(4, submission.answerText());
            statement.setString(5, submission.answerJson());
            statement.setString(6, submission.fileUrl());
            statement.setString(7, submission.language());
            statement.setString(8, submission.submitStatus().name());
            statement.setString(9, submission.evaluationStatus().name());
            statement.setString(10, submission.reviewStatus().name());
            statement.setObject(11, submission.autoScore());
            statement.setObject(12, submission.manualScore());
            statement.setObject(13, submission.finalScore());
            statement.setString(14, submission.comment());
            statement.setInt(15, submission.version());
            statement.setBoolean(16, submission.isFinal());
            statement.setTimestamp(17, Timestamp.valueOf(submission.submittedAt()));
            statement.setObject(18, submission.reviewedBy());
            statement.setObject(19, submission.reviewedAt() == null ? null : Timestamp.valueOf(submission.reviewedAt()));
            statement.setTimestamp(20, Timestamp.valueOf(submission.createdAt()));
            statement.setTimestamp(21, Timestamp.valueOf(submission.updatedAt()));
            statement.setBoolean(22, submission.deleted());
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("failed to reload saved homework submission"));
    }

    @Override
    public HomeworkSubmission update(HomeworkSubmission submission) {
        int updated = jdbcTemplate.update("""
                UPDATE t_hwk_submission
                SET submit_type = ?,
                    answer_text = ?,
                    answer_json = ?,
                    file_url = ?,
                    language = ?,
                    submit_status = ?,
                    evaluation_status = ?,
                    review_status = ?,
                    auto_score = ?,
                    manual_score = ?,
                    final_score = ?,
                    comment = ?,
                    version = ?,
                    is_final = ?,
                    submitted_at = ?,
                    reviewed_by = ?,
                    reviewed_at = ?,
                    updated_at = ?,
                    is_deleted = ?
                WHERE id = ?
                """,
                submission.submitType().name(),
                submission.answerText(),
                submission.answerJson(),
                submission.fileUrl(),
                submission.language(),
                submission.submitStatus().name(),
                submission.evaluationStatus().name(),
                submission.reviewStatus().name(),
                submission.autoScore(),
                submission.manualScore(),
                submission.finalScore(),
                submission.comment(),
                submission.version(),
                submission.isFinal(),
                Timestamp.valueOf(submission.submittedAt()),
                submission.reviewedBy(),
                submission.reviewedAt() == null ? null : Timestamp.valueOf(submission.reviewedAt()),
                Timestamp.valueOf(submission.updatedAt()),
                submission.deleted(),
                submission.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("homework submission not found");
        }
        return findById(submission.id()).orElseThrow(() -> new IllegalStateException("failed to reload updated homework submission"));
    }

    @Override
    public Optional<HomeworkSubmission> findLatestFinalByHomeworkIdAndStudentId(long homeworkId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT id, homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                               submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                               comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at,
                               updated_at, is_deleted
                        FROM t_hwk_submission
                        WHERE homework_id = ? AND student_id = ? AND is_deleted = FALSE AND is_final = TRUE
                        ORDER BY version DESC, id DESC
                        LIMIT 1
                        """,
                SUBMISSION_ROW_MAPPER,
                homeworkId,
                studentId
        ).stream().findFirst();
    }

    private Optional<HomeworkSubmission> findById(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                               submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                               comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at,
                               updated_at, is_deleted
                        FROM t_hwk_submission
                        WHERE id = ?
                        """,
                SUBMISSION_ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
