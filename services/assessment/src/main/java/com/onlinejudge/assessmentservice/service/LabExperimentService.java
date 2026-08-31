package com.onlinejudge.assessmentservice.service;

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

/** LAB's lifecycle aggregate; generic Assessment tasks are created only for an accepted LAB submission. */
@Service
public class LabExperimentService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public LabExperimentService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    LabExperimentService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
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
                       auto_evaluate, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, command.courseId());
            statement.setString(2, command.title().trim());
            statement.setString(3, command.description().trim());
            statement.setTimestamp(4, Timestamp.from(command.deadline()));
            statement.setBigDecimal(5, command.maxScore());
            statement.setString(6, String.join(",", command.allowedLanguages()));
            statement.setBoolean(7, command.autoEvaluate());
            statement.setString(8, teacherId);
            statement.setTimestamp(9, Timestamp.from(now));
            statement.setTimestamp(10, Timestamp.from(now));
            return statement;
        }, id);
        Number generated = id.getKey();
        if (generated == null) throw new IllegalStateException("LAB creation did not return an id");
        return new LabSummary(generated.longValue(), command.courseId(), command.title().trim(), "DRAFT", command.deadline(), command.maxScore(), command.autoEvaluate(), now);
    }

    public LabSummary find(long labId) {
        return jdbc.query("""
                SELECT id, course_id, title, status, deadline, max_score, auto_evaluate, created_at
                  FROM assessment_lab_experiment WHERE id = ?
                """, (rs, ignored) -> new LabSummary(rs.getLong("id"), rs.getString("course_id"),
                rs.getString("title"), rs.getString("status"), rs.getTimestamp("deadline").toInstant(),
                rs.getBigDecimal("max_score"), rs.getBoolean("auto_evaluate"), rs.getTimestamp("created_at").toInstant()), labId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("LAB does not exist"));
    }

    public List<LabSummary> list(String courseId, boolean includeDrafts) {
        String visibility = includeDrafts ? "" : " AND status <> 'DRAFT'";
        return jdbc.query("""
                SELECT id, course_id, title, status, deadline, max_score, auto_evaluate, created_at
                  FROM assessment_lab_experiment
                 WHERE course_id = ?
                """ + visibility + " ORDER BY deadline ASC, id ASC", (rs, ignored) -> new LabSummary(
                rs.getLong("id"), rs.getString("course_id"), rs.getString("title"), rs.getString("status"),
                rs.getTimestamp("deadline").toInstant(), rs.getBigDecimal("max_score"), rs.getBoolean("auto_evaluate"),
                rs.getTimestamp("created_at").toInstant()), courseId);
    }

    @Transactional
    public LabSummary publish(long labId) {
        LabSummary current = find(labId);
        if (!"DRAFT".equals(current.status())) throw new IllegalStateException("only a draft LAB can be published");
        Instant now = clock.instant();
        if (jdbc.update("UPDATE assessment_lab_experiment SET status = 'PUBLISHED', updated_at = ? WHERE id = ? AND status = 'DRAFT'",
                Timestamp.from(now), labId) != 1) {
            throw new IllegalStateException("LAB lifecycle changed concurrently");
        }
        return new LabSummary(current.labId(), current.courseId(), current.title(), "PUBLISHED", current.deadline(),
                current.maxScore(), current.autoEvaluate(), current.createdAt());
    }

    private void validate(CreateLabCommand command) {
        if (command.courseId() == null || command.courseId().isBlank()) throw new IllegalArgumentException("courseId is required");
        if (command.title() == null || command.title().isBlank() || command.title().trim().length() > 100) throw new IllegalArgumentException("title must be 1-100 characters");
        if (command.description() == null || command.description().isBlank()) throw new IllegalArgumentException("description is required");
        if (command.deadline() == null || !command.deadline().isAfter(clock.instant())) throw new IllegalArgumentException("deadline must be in the future");
        if (command.maxScore() == null || command.maxScore().signum() <= 0) throw new IllegalArgumentException("maxScore must be positive");
        if (command.allowedLanguages() == null || command.allowedLanguages().isEmpty() || command.allowedLanguages().stream().anyMatch(value -> value == null || value.isBlank() || value.contains(","))) throw new IllegalArgumentException("at least one valid language is required");
    }

    public record CreateLabCommand(String courseId, String title, String description, Instant deadline,
                                   BigDecimal maxScore, List<String> allowedLanguages, boolean autoEvaluate) { }
    public record LabSummary(long labId, String courseId, String title, String status, Instant deadline,
                             BigDecimal maxScore, boolean autoEvaluate, Instant createdAt) {
        /** Existing web client uses id, while the API-LAB example also exposes labId. */
        @com.fasterxml.jackson.annotation.JsonProperty("id")
        public long id() { return labId; }
    }
}
