package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** LAB's lifecycle aggregate; generic Assessment tasks are created only for an accepted LAB submission. */
@Service
public class LabExperimentService {
    private final JdbcTemplate jdbc;
    private final AssessmentOutboxRepository outbox;
    private final SourceGradeRepository grades;
    private final Clock clock;

    public LabExperimentService(JdbcTemplate jdbc, AssessmentOutboxRepository outbox) {
        this(jdbc, outbox, null, Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LabExperimentService(JdbcTemplate jdbc, AssessmentOutboxRepository outbox, SourceGradeRepository grades) {
        this(jdbc, outbox, grades, Clock.systemUTC());
    }

    LabExperimentService(JdbcTemplate jdbc, AssessmentOutboxRepository outbox, Clock clock) {
        this(jdbc, outbox, null, clock);
    }

    LabExperimentService(JdbcTemplate jdbc, AssessmentOutboxRepository outbox, SourceGradeRepository grades, Clock clock) {
        this.jdbc = jdbc; this.outbox = outbox; this.grades = grades; this.clock = clock;
    }

    @Transactional
    public LabSummary create(CreateLabCommand command, String teacherId) {
        validate(command);
        Instant now = clock.instant();
        KeyHolder id = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO assessment_lab_experiment
                      (course_id, title, description, status, deadline, max_score, allowed_languages,
                       auto_evaluate, chapter_id, attachment_ids, evaluation_mode, report_required,
                       time_limit_ms, memory_limit_kb, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, command.courseId());
            statement.setString(2, command.title().trim());
            statement.setString(3, command.description().trim());
            statement.setTimestamp(4, Timestamp.from(command.deadline()));
            statement.setBigDecimal(5, command.maxScore());
            statement.setString(6, String.join(",", command.allowedLanguages()));
            statement.setBoolean(7, command.autoEvaluate());
            statement.setObject(8, command.chapterId());
            statement.setString(9, command.attachmentIds());
            statement.setString(10, command.evaluationMode());
            statement.setBoolean(11, command.reportRequired());
            statement.setInt(12, command.timeLimitMs());
            statement.setInt(13, command.memoryLimitKb());
            statement.setString(14, teacherId);
            statement.setTimestamp(15, Timestamp.from(now));
            statement.setTimestamp(16, Timestamp.from(now));
            return statement;
        }, id);
        Number generated = id.getKey();
        if (generated == null) throw new IllegalStateException("LAB creation did not return an id");
        for (LabTestcase testcase : command.testcases()) {
            jdbc.update("""
                    INSERT INTO assessment_lab_testcase (lab_id, input_text, expected_output, score_weight, is_public, order_num)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, generated.longValue(), testcase.input(), testcase.expectedOutput(), testcase.scoreWeight(), testcase.isPublic(), testcase.orderNum());
        }
        return new LabSummary(generated.longValue(), command.courseId(), command.title().trim(), "DRAFT", command.deadline(), command.maxScore(), command.autoEvaluate(), now,
                command.evaluationMode(), command.reportRequired(), null, false);
    }

    public LabSummary find(long labId) {
        return jdbc.query("""
                SELECT id, course_id, title, status, deadline, max_score, auto_evaluate, created_at,
                       evaluation_mode, report_required, published_at, deleted
                  FROM assessment_lab_experiment WHERE id = ? AND deleted = FALSE
                """, (rs, ignored) -> new LabSummary(rs.getLong("id"), rs.getString("course_id"),
                rs.getString("title"), rs.getString("status"), rs.getTimestamp("deadline").toInstant(),
                rs.getBigDecimal("max_score"), rs.getBoolean("auto_evaluate"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("evaluation_mode"), rs.getBoolean("report_required"), toInstant(rs.getTimestamp("published_at")), rs.getBoolean("deleted")), labId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("LAB does not exist"));
    }

    public List<LabSummary> list(String courseId, boolean includeDrafts) {
        String visibility = includeDrafts ? "" : " AND status <> 'DRAFT'";
        return jdbc.query("""
                SELECT id, course_id, title, status, deadline, max_score, auto_evaluate, created_at,
                       evaluation_mode, report_required, published_at, deleted
                  FROM assessment_lab_experiment
                 WHERE course_id = ? AND deleted = FALSE
                """ + visibility + " ORDER BY deadline ASC, id ASC", (rs, ignored) -> new LabSummary(
                rs.getLong("id"), rs.getString("course_id"), rs.getString("title"), rs.getString("status"),
                rs.getTimestamp("deadline").toInstant(), rs.getBigDecimal("max_score"), rs.getBoolean("auto_evaluate"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("evaluation_mode"),
                rs.getBoolean("report_required"), toInstant(rs.getTimestamp("published_at")), rs.getBoolean("deleted")), courseId);
    }

    public LabDetail detail(long labId) {
        LabSummary summary = find(labId);
        LabMetadata metadata = jdbc.query("""
                SELECT description, chapter_id, attachment_ids, allowed_languages, time_limit_ms, memory_limit_kb
                  FROM assessment_lab_experiment WHERE id = ? AND deleted = FALSE
                """, (rs, ignored) -> new LabMetadata(rs.getString("description"),
                (Long) rs.getObject("chapter_id"), rs.getString("attachment_ids"), rs.getString("allowed_languages"),
                rs.getInt("time_limit_ms"), rs.getInt("memory_limit_kb")), labId).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("LAB does not exist"));
        List<LabTestcase> testcases = jdbc.query("""
                SELECT id, input_text, expected_output, score_weight, is_public, order_num
                  FROM assessment_lab_testcase WHERE lab_id = ? ORDER BY order_num, id
                """, (rs, ignored) -> new LabTestcase(rs.getLong("id"), labId, rs.getString("input_text"),
                rs.getString("expected_output"), rs.getBigDecimal("score_weight"), rs.getBoolean("is_public"), rs.getInt("order_num")), labId);
        return new LabDetail(summary, metadata.description(), metadata.chapterId(), metadata.attachmentIds(),
                metadata.allowedLanguages(), metadata.timeLimitMs(), metadata.memoryLimitKb(), testcases);
    }

    @Transactional
    public LabSummary update(long labId, UpdateLabCommand command) {
        LabSummary current = find(labId);
        if (!"DRAFT".equals(current.status())) throw new IllegalStateException("only a draft LAB can be updated");
        validate(command);
        Instant now = clock.instant();
        if (jdbc.update("""
                UPDATE assessment_lab_experiment SET title = ?, description = ?, deadline = ?, max_score = ?,
                    allowed_languages = ?, auto_evaluate = ?, chapter_id = ?, attachment_ids = ?, evaluation_mode = ?,
                    report_required = ?, time_limit_ms = ?, memory_limit_kb = ?, updated_at = ?
                 WHERE id = ? AND status = 'DRAFT' AND deleted = FALSE
                """, command.title().trim(), command.description().trim(), Timestamp.from(command.deadline()), command.maxScore(),
                String.join(",", command.allowedLanguages()), command.autoEvaluate(), command.chapterId(), command.attachmentIds(), command.evaluationMode(),
                command.reportRequired(), command.timeLimitMs(), command.memoryLimitKb(), Timestamp.from(now), labId) != 1) {
            throw new IllegalStateException("LAB lifecycle changed concurrently");
        }
        jdbc.update("DELETE FROM assessment_lab_testcase WHERE lab_id = ?", labId);
        for (LabTestcase testcase : command.testcases()) {
            jdbc.update("""
                    INSERT INTO assessment_lab_testcase (lab_id, input_text, expected_output, score_weight, is_public, order_num)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, labId, testcase.input(), testcase.expectedOutput(), testcase.scoreWeight(), testcase.isPublic(), testcase.orderNum());
        }
        return find(labId);
    }

    @Transactional
    public LabSummary delete(long labId) {
        LabSummary current = find(labId);
        if (!"DRAFT".equals(current.status())) throw new IllegalStateException("only a draft LAB can be deleted");
        Instant now = clock.instant();
        jdbc.update("UPDATE assessment_lab_experiment SET deleted = TRUE, status = 'ARCHIVED', updated_at = ? WHERE id = ? AND status = 'DRAFT'", Timestamp.from(now), labId);
        return new LabSummary(current.labId(), current.courseId(), current.title(), "ARCHIVED", current.deadline(), current.maxScore(), current.autoEvaluate(), current.createdAt(), current.evaluationMode(), current.reportRequired(), current.publishedAt(), true);
    }

    @Transactional
    public LabSummary close(long labId) {
        LabSummary current = find(labId);
        if (!"PUBLISHED".equals(current.status())) throw new IllegalStateException("only a published LAB can be closed");
        Instant now = clock.instant();
        if (jdbc.update("UPDATE assessment_lab_experiment SET status = 'CLOSED', updated_at = ? WHERE id = ? AND status = 'PUBLISHED'", Timestamp.from(now), labId) != 1) {
            throw new IllegalStateException("LAB lifecycle changed concurrently");
        }
        return new LabSummary(current.labId(), current.courseId(), current.title(), "CLOSED", current.deadline(), current.maxScore(), current.autoEvaluate(), current.createdAt(), current.evaluationMode(), current.reportRequired(), current.publishedAt(), false);
    }

    @Transactional
    public LabSummary releaseScores(long labId) {
        return releaseScores(labId, UUID.randomUUID().toString());
    }

    @Transactional
    public LabSummary releaseScores(long labId, String requestId) {
        requireRequestId(requestId);
        LabSummary current = find(labId);
        if (!"PUBLISHED".equals(current.status()) && !"CLOSED".equals(current.status())) throw new IllegalStateException("only an open or closed LAB can publish scores");
        Instant now = clock.instant();
        if (jdbc.update("UPDATE assessment_lab_experiment SET status = 'SCORE_PUBLISHED', updated_at = ? WHERE id = ? AND status IN ('PUBLISHED', 'CLOSED')", Timestamp.from(now), labId) != 1) {
            throw new IllegalStateException("LAB lifecycle changed concurrently");
        }
        publishStoredScores(current, now, requestId);
        return new LabSummary(current.labId(), current.courseId(), current.title(), "SCORE_PUBLISHED", current.deadline(), current.maxScore(), current.autoEvaluate(), current.createdAt(), current.evaluationMode(), current.reportRequired(), current.publishedAt(), false);
    }

    private void publishStoredScores(LabSummary lab, Instant now, String requestId) {
        if (grades == null || outbox == null) return;
        jdbc.query("""
                SELECT s.submission_id, s.student_id, COALESCE(s.final_score, s.auto_score) AS score
                  FROM assessment_lab_submission s
                 WHERE s.lab_id = ?
                   AND ((s.final_score IS NOT NULL AND s.submission_version = (SELECT MAX(s2.submission_version) FROM assessment_lab_submission s2 WHERE s2.lab_id = s.lab_id AND s2.student_id = s.student_id AND s2.final_score IS NOT NULL))
                     OR (s.final_score IS NULL AND NOT EXISTS (SELECT 1 FROM assessment_lab_submission sf WHERE sf.lab_id = s.lab_id AND sf.student_id = s.student_id AND sf.final_score IS NOT NULL)
                         AND s.submission_version = (SELECT MAX(s3.submission_version) FROM assessment_lab_submission s3 WHERE s3.lab_id = s.lab_id AND s3.student_id = s.student_id)))
                """, (rs, ignored) -> {
            String studentId = rs.getString("student_id");
            BigDecimal score = rs.getBigDecimal("score");
            String status = score == null ? "UNGRADED" : "SCORED";
            long version = score == null
                    ? grades.upsertUngraded("LAB", Long.toString(lab.labId()), lab.courseId(), studentId, lab.maxScore(), now)
                    : grades.upsertScored("LAB", Long.toString(lab.labId()), lab.courseId(), studentId, score, lab.maxScore(), now);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("courseId", lab.courseId());
            payload.put("sourceType", "LAB");
            payload.put("sourceId", Long.toString(lab.labId()));
            payload.put("studentId", studentId);
            payload.put("score", score);
            payload.put("fullScore", lab.maxScore());
            payload.put("status", status);
            payload.put("sourceVersion", version);
            outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", "LAB:" + lab.labId() + ":" + studentId,
                    version, requestId, payload, now);
            return null;
        }, lab.labId());
    }

    @Transactional
    public LabSummary publish(long labId) {
        LabSummary current = find(labId);
        if (!"DRAFT".equals(current.status())) throw new IllegalStateException("only a draft LAB can be published");
        Instant now = clock.instant();
        if (jdbc.update("UPDATE assessment_lab_experiment SET status = 'PUBLISHED', published_at = ?, updated_at = ? WHERE id = ? AND status = 'DRAFT'",
                Timestamp.from(now), Timestamp.from(now), labId) != 1) {
            throw new IllegalStateException("LAB lifecycle changed concurrently");
        }
        outbox.append("assessment.lab.published.v2", "assessment-lab", Long.toString(labId), 2,
                java.util.UUID.randomUUID().toString(), java.util.Map.of(
                        "courseId", current.courseId(), "labId", Long.toString(labId), "title", current.title(),
                        "deadline", current.deadline().toString(), "receiverScope", "COURSE_ACTIVE_STUDENTS", "publishedAt", now.toString()), now);
        return new LabSummary(current.labId(), current.courseId(), current.title(), "PUBLISHED", current.deadline(),
                current.maxScore(), current.autoEvaluate(), current.createdAt(), current.evaluationMode(), current.reportRequired(), now, false);
    }

    private void validate(CreateLabCommand command) {
        if (command.timeLimitMs() <= 0 || command.memoryLimitKb() <= 0) throw new IllegalArgumentException("time and memory limits must be positive");
        if (command.courseId() == null || command.courseId().isBlank()) throw new IllegalArgumentException("courseId is required");
        if (command.title() == null || command.title().isBlank() || command.title().trim().length() > 100) throw new IllegalArgumentException("title must be 1-100 characters");
        if (command.description() == null || command.description().isBlank()) throw new IllegalArgumentException("description is required");
        if (command.deadline() == null || !command.deadline().isAfter(clock.instant())) throw new IllegalArgumentException("deadline must be in the future");
        if (command.maxScore() == null || command.maxScore().signum() <= 0) throw new IllegalArgumentException("maxScore must be positive");
        if (command.allowedLanguages() == null || command.allowedLanguages().isEmpty() || command.allowedLanguages().stream().anyMatch(value -> value == null || value.isBlank() || value.contains(","))) throw new IllegalArgumentException("at least one valid language is required");
        if (command.testcases() == null) throw new IllegalArgumentException("testcases are required");
        validateAutomaticTestcases(command.autoEvaluate(), command.maxScore(), command.testcases());
        long orders = command.testcases().stream().map(LabTestcase::orderNum).distinct().count();
        if (orders != command.testcases().size()) throw new IllegalArgumentException("testcase orderNum values must be unique");
        command.testcases().forEach(LabTestcase::validate);
    }

    private void validate(UpdateLabCommand command) {
        if (command.timeLimitMs() <= 0 || command.memoryLimitKb() <= 0) throw new IllegalArgumentException("time and memory limits must be positive");
        if (command.title() == null || command.title().isBlank() || command.title().trim().length() > 100) throw new IllegalArgumentException("title must be 1-100 characters");
        if (command.description() == null || command.description().isBlank()) throw new IllegalArgumentException("description is required");
        if (command.deadline() == null || !command.deadline().isAfter(clock.instant())) throw new IllegalArgumentException("deadline must be in the future");
        if (command.maxScore() == null || command.maxScore().signum() <= 0) throw new IllegalArgumentException("maxScore must be positive");
        if (command.allowedLanguages() == null || command.allowedLanguages().isEmpty() || command.allowedLanguages().stream().anyMatch(value -> value == null || value.isBlank() || value.contains(","))) throw new IllegalArgumentException("at least one valid language is required");
        if (command.testcases() == null) throw new IllegalArgumentException("testcases are required");
        validateAutomaticTestcases(command.autoEvaluate(), command.maxScore(), command.testcases());
        if (command.testcases().stream().map(LabTestcase::orderNum).distinct().count() != command.testcases().size()) throw new IllegalArgumentException("testcase orderNum values must be unique");
        command.testcases().forEach(LabTestcase::validate);
    }

    private void validateAutomaticTestcases(boolean autoEvaluate, BigDecimal maxScore, List<LabTestcase> testcases) {
        if (!autoEvaluate) return;
        if (testcases.isEmpty()) throw new IllegalArgumentException("automatic LAB requires at least one testcase");
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (LabTestcase testcase : testcases) {
            if (testcase == null || testcase.scoreWeight() == null || testcase.scoreWeight().signum() <= 0) {
                throw new IllegalArgumentException("automatic LAB testcase weights must be positive");
            }
            totalWeight = totalWeight.add(testcase.scoreWeight());
        }
        if (totalWeight.compareTo(maxScore) != 0) {
            throw new IllegalArgumentException("automatic LAB testcase weights must equal maxScore");
        }
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 80) throw new IllegalArgumentException("X-Request-Id must be 1-80 characters");
    }

    public record CreateLabCommand(String courseId, String title, String description, Instant deadline,
                                   BigDecimal maxScore, List<String> allowedLanguages, boolean autoEvaluate,
                                   List<LabTestcase> testcases, Long chapterId, String attachmentIds,
                                   String evaluationMode, boolean reportRequired, int timeLimitMs, int memoryLimitKb) {
        public CreateLabCommand(String courseId, String title, String description, Instant deadline,
                                BigDecimal maxScore, List<String> allowedLanguages, boolean autoEvaluate,
                                List<LabTestcase> testcases) {
            this(courseId, title, description, deadline, maxScore, allowedLanguages, autoEvaluate, testcases,
                    null, "", "DOCKER_IO", false, 30000, 262144);
        }
    }
    public record UpdateLabCommand(String title, String description, Instant deadline, BigDecimal maxScore,
                                   List<String> allowedLanguages, boolean autoEvaluate, List<LabTestcase> testcases,
                                   Long chapterId, String attachmentIds, String evaluationMode, boolean reportRequired,
                                   int timeLimitMs, int memoryLimitKb) { }
    public record LabTestcase(long id, long labId, String input, String expectedOutput, BigDecimal scoreWeight, boolean isPublic, int orderNum) {
        public LabTestcase(String input, String expectedOutput, BigDecimal scoreWeight, boolean isPublic, int orderNum) {
            this(0L, 0L, input, expectedOutput, scoreWeight, isPublic, orderNum);
        }
        void validate() {
            if (input == null || expectedOutput == null || scoreWeight == null || scoreWeight.signum() < 0 || orderNum < 0) {
                throw new IllegalArgumentException("LAB testcase is invalid");
            }
        }
    }
    public record LabSummary(long labId, String courseId, String title, String status, Instant deadline,
                             BigDecimal maxScore, boolean autoEvaluate, Instant createdAt, String evaluationMode,
                             boolean reportRequired, Instant publishedAt, boolean deleted) {
        public LabSummary(long labId, String courseId, String title, String status, Instant deadline,
                          BigDecimal maxScore, boolean autoEvaluate, Instant createdAt) {
            this(labId, courseId, title, status, deadline, maxScore, autoEvaluate, createdAt, "DOCKER_IO", false, null, false);
        }
        /** Existing web client uses id, while the API-LAB example also exposes labId. */
        @com.fasterxml.jackson.annotation.JsonProperty("id")
        public long id() { return labId; }
    }
    public record LabDetail(LabSummary summary, String description, Long chapterId, String attachmentIds,
                            String allowedLanguages, int timeLimitMs, int memoryLimitKb, List<LabTestcase> testcases) {
        public long id() { return summary.labId(); }
        public String courseId() { return summary.courseId(); }
        public String title() { return summary.title(); }
        public String status() { return summary.status(); }
        public Instant deadline() { return summary.deadline(); }
        public BigDecimal maxScore() { return summary.maxScore(); }
        public String evaluationMode() { return summary.evaluationMode(); }
        public boolean autoEvaluate() { return summary.autoEvaluate(); }
        public boolean reportRequired() { return summary.reportRequired(); }
        public Instant publishedAt() { return summary.publishedAt(); }
        public boolean deleted() { return summary.deleted(); }
    }
    private record LabMetadata(String description, Long chapterId, String attachmentIds, String allowedLanguages,
                               int timeLimitMs, int memoryLimitKb) { }
    private static Instant toInstant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
