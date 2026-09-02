package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.service.LabExperimentService.LabSummary;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns report versions and their file metadata.  The report file is stored
 * before the database row, then compensated if the database operation fails,
 * matching the LAB submission file boundary.
 */
@Service
public class LabReportService {
    private static final long MAX_REPORT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_COMMENT_LENGTH = 500;

    private final JdbcTemplate jdbc;
    private final PersistentSubmissionFileStore files;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LabReportService(JdbcTemplate jdbc, PersistentSubmissionFileStore files) {
        this(jdbc, files, Clock.systemUTC());
    }

    LabReportService(JdbcTemplate jdbc, PersistentSubmissionFileStore files, Clock clock) {
        this.jdbc = jdbc;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public LabReportSummary upload(LabSummary lab, String studentId, String requestedSubmissionId, MultipartFile reportFile)
            throws IOException {
        validateUploadable(lab, reportFile);
        String submissionId = resolveSubmissionId(lab.labId(), studentId, requestedSubmissionId);
        Instant now = clock.instant();
        PersistentSubmissionFileStore.StoredFile stored = null;
        try {
            String originalFilename = reportFile.getOriginalFilename();
            stored = files.store("report-" + UUID.randomUUID(), originalFilename, reportFile.getBytes());
            String type = fileType(stored.originalFilename());
            String contentType = normalizedContentType(type);
            int version = nextVersion(lab.labId(), studentId);
            long reportId = insert(lab.labId(), studentId, submissionId, stored, type, contentType, version, now);
            return summary(reportId);
        } catch (RuntimeException | IOException failure) {
            if (stored != null) {
                try { files.delete(stored.storageKey()); }
                catch (IOException ignored) { /* residual storage cleanup is observable infrastructure work */ }
            }
            throw failure;
        }
    }

    public LabReportSummary get(long labId, long reportId) {
        return summary(row(labId, reportId));
    }

    public ReportFile file(long labId, long reportId) {
        ReportRow row = row(labId, reportId);
        return new ReportFile(summary(row), row.studentId(), row.storageKey(), row.contentType());
    }

    public byte[] read(String storageKey) throws IOException {
        return files.read(storageKey);
    }

    public LabReportSummary latestForSubmission(long labId, String submissionId) {
        if (submissionId == null || submissionId.isBlank()) return null;
        return jdbc.query("""
                SELECT id, lab_id, student_id, submission_id, storage_key, file_name, file_type, content_type, file_size,
                       report_version, score, comment, submitted_at, scored_by, scored_at, updated_at
                  FROM assessment_lab_report
                 WHERE lab_id = ? AND submission_id = ?
                 ORDER BY report_version DESC, id DESC
                 LIMIT 1
                """, (rs, ignored) -> mapRow(rs), labId, submissionId)
                .stream().findFirst().map(this::summary).orElse(null);
    }

    @Transactional
    public LabReportSummary score(long labId, long reportId, BigDecimal score, String comment, String teacherId,
            BigDecimal maxScore) {
        if (score == null || score.signum() < 0 || score.compareTo(maxScore) > 0) {
            throw new IllegalArgumentException("report score must be within the LAB score range");
        }
        String normalizedComment = comment == null ? null : comment.trim();
        if (normalizedComment != null && normalizedComment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("report comment must be at most 500 characters");
        }
        if (normalizedComment != null && normalizedComment.isEmpty()) normalizedComment = null;
        ReportRow existing = row(labId, reportId);
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE assessment_lab_report
                   SET score = ?, comment = ?, scored_by = ?, scored_at = ?, updated_at = ?
                 WHERE id = ? AND lab_id = ?
                """, score, normalizedComment, teacherId, Timestamp.from(now), Timestamp.from(now), existing.reportId(), labId);
        return summary(reportId);
    }

    private void validateUploadable(LabSummary lab, MultipartFile reportFile) {
        if (!"PUBLISHED".equals(lab.status()) || !clock.instant().isBefore(lab.deadline())) {
            throw new IllegalStateException("LAB is not open for report submissions");
        }
        if (reportFile == null || reportFile.isEmpty()) throw new IllegalArgumentException("report file is required");
        if (reportFile.getSize() > MAX_REPORT_SIZE_BYTES) throw new IllegalArgumentException("report file exceeds 10MB");
        fileType(reportFile.getOriginalFilename());
    }

    private String resolveSubmissionId(long labId, String studentId, String requestedSubmissionId) {
        if (requestedSubmissionId == null || requestedSubmissionId.isBlank()) {
            return jdbc.query("""
                    SELECT submission_id FROM assessment_lab_submission
                     WHERE lab_id = ? AND student_id = ?
                     ORDER BY submission_version DESC, submitted_at DESC
                     LIMIT 1
                    """, rows -> rows.next() ? rows.getString(1) : null, labId, studentId);
        }
        boolean belongsToStudent = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_lab_submission
                 WHERE lab_id = ? AND submission_id = ? AND student_id = ?
                """, Integer.class, labId, requestedSubmissionId, studentId) == 1;
        if (!belongsToStudent) throw new NoSuchElementException("LAB submission does not exist");
        return requestedSubmissionId;
    }

    private int nextVersion(long labId, String studentId) {
        Integer current = jdbc.queryForObject("""
                SELECT COALESCE(MAX(report_version), 0) FROM assessment_lab_report
                 WHERE lab_id = ? AND student_id = ?
                """, Integer.class, labId, studentId);
        return (current == null ? 0 : current) + 1;
    }

    private long insert(long labId, String studentId, String submissionId, PersistentSubmissionFileStore.StoredFile stored,
            String fileType, String contentType, int version, Instant now) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO assessment_lab_report
                        (lab_id, student_id, submission_id, storage_key, file_name, file_type, content_type, file_size,
                         report_version, score, comment, submitted_at, scored_by, scored_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, NULL, NULL, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, labId);
            statement.setString(2, studentId);
            statement.setString(3, submissionId);
            statement.setString(4, stored.storageKey());
            statement.setString(5, stored.originalFilename());
            statement.setString(6, fileType);
            statement.setString(7, contentType);
            statement.setLong(8, stored.size());
            statement.setInt(9, version);
            statement.setTimestamp(10, Timestamp.from(now));
            statement.setTimestamp(11, Timestamp.from(now));
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("report persistence did not return an id");
        return id.longValue();
    }

    private LabReportSummary summary(long reportId) {
        return summary(rowById(reportId));
    }

    private LabReportSummary summary(ReportRow row) {
        return new LabReportSummary(row.reportId(), row.submissionId(), row.fileName(), row.fileType(), row.fileSize(),
                row.version(), row.score(), row.comment(), row.submittedAt(),
                "/api/v1/labs/" + row.labId() + "/reports/" + row.reportId() + "/download");
    }

    private ReportRow row(long labId, long reportId) {
        return jdbc.query("""
                SELECT id, lab_id, student_id, submission_id, storage_key, file_name, file_type, content_type, file_size,
                       report_version, score, comment, submitted_at, scored_by, scored_at, updated_at
                  FROM assessment_lab_report
                 WHERE id = ? AND lab_id = ?
                """, (rs, ignored) -> mapRow(rs), reportId, labId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("LAB report does not exist"));
    }

    private ReportRow rowById(long reportId) {
        return jdbc.query("""
                SELECT id, lab_id, student_id, submission_id, storage_key, file_name, file_type, content_type, file_size,
                       report_version, score, comment, submitted_at, scored_by, scored_at, updated_at
                  FROM assessment_lab_report WHERE id = ?
                """, (rs, ignored) -> mapRow(rs), reportId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("LAB report does not exist"));
    }

    private static ReportRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp scoredAt = rs.getTimestamp("scored_at");
        return new ReportRow(rs.getLong("id"), rs.getLong("lab_id"), rs.getString("student_id"),
                rs.getString("submission_id"), rs.getString("storage_key"), rs.getString("file_name"),
                rs.getString("file_type"), rs.getString("content_type"), rs.getLong("file_size"),
                rs.getInt("report_version"), rs.getBigDecimal("score"), rs.getString("comment"),
                rs.getTimestamp("submitted_at").toInstant(), rs.getString("scored_by"),
                scoredAt == null ? null : scoredAt.toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static String fileType(String filename) {
        String name = filename == null ? "" : filename.trim();
        int index = name.lastIndexOf('.');
        String extension = index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> "PDF";
            case "docx" -> "DOCX";
            case "zip" -> "ZIP";
            default -> throw new IllegalArgumentException("report file must be PDF, DOCX, or ZIP");
        };
    }

    private static String normalizedContentType(String fileType) {
        return switch (fileType) {
            case "PDF" -> "application/pdf";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/zip";
        };
    }

    public record LabReportSummary(long reportId, String submissionId, String fileName, String fileType, long fileSize,
                                   int version, BigDecimal score, String comment, Instant submittedAt, String downloadUrl) { }
    public record ReportFile(LabReportSummary summary, String studentId, String storageKey, String contentType) { }
    private record ReportRow(long reportId, long labId, String studentId, String submissionId, String storageKey,
                             String fileName, String fileType, String contentType, long fileSize, int version,
                             BigDecimal score, String comment, Instant submittedAt, String scoredBy, Instant scoredAt,
                             Instant updatedAt) { }
}
