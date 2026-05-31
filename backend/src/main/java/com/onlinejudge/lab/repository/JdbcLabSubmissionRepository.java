package com.onlinejudge.lab.repository;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcLabSubmissionRepository implements LabSubmissionRepository {
    private static final RowMapper<LabSubmission> SUBMISSION_ROW_MAPPER = (resultSet, rowNum) -> new LabSubmission(
            resultSet.getLong("id"),
            resultSet.getLong("lab_id"),
            resultSet.getLong("student_id"),
            resultSet.getString("code_content"),
            resultSet.getString("file_id"),
            resultSet.getString("language"),
            LabSubmitStatus.valueOf(resultSet.getString("submit_status")),
            EvaluationStatus.valueOf(resultSet.getString("evaluation_status")),
            resultSet.getObject("final_score", Integer.class),
            resultSet.getObject("auto_score", Integer.class),
            resultSet.getInt("version"),
            resultSet.getBoolean("is_final"),
            resultSet.getTimestamp("submitted_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(),
            resultSet.getBoolean("deleted")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabSubmissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabSubmission save(LabSubmission submission) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_submission
                    (lab_id, student_id, code_content, file_id, language, submit_status, evaluation_status,
                     final_score, auto_score, version, is_final, submitted_at, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, submission.labId());
            statement.setLong(2, submission.studentId());
            statement.setString(3, submission.codeContent());
            statement.setString(4, submission.fileId());
            statement.setString(5, submission.language());
            statement.setString(6, submission.submitStatus().name());
            statement.setString(7, submission.evaluationStatus().name());
            statement.setObject(8, submission.finalScore());
            statement.setObject(9, submission.autoScore());
            statement.setInt(10, submission.version());
            statement.setBoolean(11, submission.isFinal());
            statement.setTimestamp(12, Timestamp.valueOf(submission.submittedAt()));
            statement.setTimestamp(13, Timestamp.valueOf(submission.createdAt()));
            statement.setTimestamp(14, Timestamp.valueOf(submission.updatedAt()));
            statement.setBoolean(15, submission.deleted());
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("保存提交后无法读取记录"));
    }

    @Override
    public LabSubmission update(LabSubmission submission) {
        int updated = jdbcTemplate.update("""
                UPDATE lab_submission
                SET code_content = ?,
                    file_id = ?,
                    language = ?,
                    submit_status = ?,
                    evaluation_status = ?,
                    final_score = ?,
                    auto_score = ?,
                    version = ?,
                    is_final = ?,
                    submitted_at = ?,
                    updated_at = ?,
                    deleted = ?
                WHERE id = ?
                """,
                submission.codeContent(),
                submission.fileId(),
                submission.language(),
                submission.submitStatus().name(),
                submission.evaluationStatus().name(),
                submission.finalScore(),
                submission.autoScore(),
                submission.version(),
                submission.isFinal(),
                Timestamp.valueOf(submission.submittedAt()),
                Timestamp.valueOf(submission.updatedAt()),
                submission.deleted(),
                submission.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        return findById(submission.id()).orElseThrow(() -> new IllegalStateException("更新提交后无法读取记录"));
    }

    @Override
    public Optional<LabSubmission> findLatestFinalByLabIdAndStudentId(long labId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, code_content, file_id, language, submit_status,
                               evaluation_status, final_score, auto_score, version, is_final, submitted_at,
                               created_at, updated_at, deleted
                        FROM lab_submission
                        WHERE lab_id = ? AND student_id = ? AND deleted = FALSE AND is_final = TRUE
                        ORDER BY version DESC, id DESC
                        LIMIT 1
                        """,
                SUBMISSION_ROW_MAPPER,
                labId,
                studentId
        ).stream().findFirst();
    }

    @Override
    public List<LabSubmission> findByLabId(long labId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, code_content, file_id, language, submit_status,
                               evaluation_status, final_score, auto_score, version, is_final, submitted_at,
                               created_at, updated_at, deleted
                        FROM lab_submission
                        WHERE lab_id = ? AND deleted = FALSE
                        ORDER BY submitted_at DESC, version DESC, id DESC
                        """,
                SUBMISSION_ROW_MAPPER,
                labId
        );
    }

    @Override
    public List<LabSubmission> findByLabIdAndStudentId(long labId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, code_content, file_id, language, submit_status,
                               evaluation_status, final_score, auto_score, version, is_final, submitted_at,
                               created_at, updated_at, deleted
                        FROM lab_submission
                        WHERE lab_id = ? AND student_id = ? AND deleted = FALSE
                        ORDER BY submitted_at DESC, version DESC, id DESC
                        """,
                SUBMISSION_ROW_MAPPER,
                labId,
                studentId
        );
    }

    @Override
    public Optional<LabSubmission> findById(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, code_content, file_id, language, submit_status,
                               evaluation_status, final_score, auto_score, version, is_final, submitted_at,
                               created_at, updated_at, deleted
                        FROM lab_submission
                        WHERE id = ?
                        """,
                SUBMISSION_ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }
}
