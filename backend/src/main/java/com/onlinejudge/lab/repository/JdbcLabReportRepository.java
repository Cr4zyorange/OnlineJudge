package com.onlinejudge.lab.repository;

import com.onlinejudge.lab.domain.LabReport;
import com.onlinejudge.lab.domain.LabReportFileType;
import com.onlinejudge.lab.domain.LabReportRepository;
import com.onlinejudge.lab.domain.LabReportSubmitStatus;
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
public class JdbcLabReportRepository implements LabReportRepository {
    private static final RowMapper<LabReport> REPORT_ROW_MAPPER = (resultSet, rowNum) -> new LabReport(
            resultSet.getLong("id"),
            resultSet.getLong("lab_id"),
            resultSet.getLong("student_id"),
            resultSet.getObject("submission_id", Long.class),
            resultSet.getString("file_id"),
            resultSet.getString("file_name"),
            LabReportFileType.valueOf(resultSet.getString("file_type")),
            resultSet.getLong("file_size"),
            resultSet.getInt("version"),
            LabReportSubmitStatus.valueOf(resultSet.getString("submit_status")),
            resultSet.getObject("score", Integer.class),
            resultSet.getString("comment"),
            resultSet.getTimestamp("submitted_at").toLocalDateTime(),
            resultSet.getObject("scored_by", Long.class),
            resultSet.getTimestamp("scored_at") == null ? null : resultSet.getTimestamp("scored_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabReport save(LabReport report) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_report
                        (lab_id, student_id, submission_id, file_id, file_name, file_type, file_size, version,
                         submit_status, score, comment, submitted_at, scored_by, scored_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, report.labId());
            statement.setLong(2, report.studentId());
            statement.setObject(3, report.submissionId());
            statement.setString(4, report.fileId());
            statement.setString(5, report.fileName());
            statement.setString(6, report.fileType().name());
            statement.setLong(7, report.fileSize());
            statement.setInt(8, report.version());
            statement.setString(9, report.submitStatus().name());
            statement.setObject(10, report.score());
            statement.setString(11, report.comment());
            statement.setTimestamp(12, Timestamp.valueOf(report.submittedAt()));
            statement.setObject(13, report.scoredBy());
            statement.setTimestamp(14, report.scoredAt() == null ? null : Timestamp.valueOf(report.scoredAt()));
            statement.setTimestamp(15, Timestamp.valueOf(report.createdAt()));
            statement.setTimestamp(16, Timestamp.valueOf(report.updatedAt()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("保存报告后无法读取记录"));
    }

    @Override
    public LabReport updateScore(LabReport report) {
        jdbcTemplate.update("""
                        UPDATE lab_report
                        SET score = ?,
                            comment = ?,
                            scored_by = ?,
                            scored_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                report.score(),
                report.comment(),
                report.scoredBy(),
                report.scoredAt() == null ? null : Timestamp.valueOf(report.scoredAt()),
                Timestamp.valueOf(report.updatedAt()),
                report.id()
        );
        return findById(report.id()).orElseThrow(() -> new IllegalStateException("更新报告评分后无法读取记录"));
    }

    @Override
    public Optional<LabReport> findById(long reportId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, submission_id, file_id, file_name, file_type, file_size,
                               version, submit_status, score, comment, submitted_at, scored_by, scored_at,
                               created_at, updated_at
                        FROM lab_report
                        WHERE id = ?
                        """,
                REPORT_ROW_MAPPER,
                reportId
        ).stream().findFirst();
    }

    @Override
    public Optional<LabReport> findLatestBySubmissionId(long submissionId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, submission_id, file_id, file_name, file_type, file_size,
                               version, submit_status, score, comment, submitted_at, scored_by, scored_at,
                               created_at, updated_at
                        FROM lab_report
                        WHERE submission_id = ?
                        ORDER BY version DESC, id DESC
                        LIMIT 1
                        """,
                REPORT_ROW_MAPPER,
                submissionId
        ).stream().findFirst();
    }

    @Override
    public Optional<LabReport> findLatestByLabIdAndStudentId(long labId, long studentId) {
        return jdbcTemplate.query("""
                        SELECT id, lab_id, student_id, submission_id, file_id, file_name, file_type, file_size,
                               version, submit_status, score, comment, submitted_at, scored_by, scored_at,
                               created_at, updated_at
                        FROM lab_report
                        WHERE lab_id = ? AND student_id = ?
                        ORDER BY version DESC, id DESC
                        LIMIT 1
                        """,
                REPORT_ROW_MAPPER,
                labId,
                studentId
        ).stream().findFirst();
    }
}
