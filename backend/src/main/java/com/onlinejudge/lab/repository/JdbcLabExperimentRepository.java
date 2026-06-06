package com.onlinejudge.lab.repository;

import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabTestcase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcLabExperimentRepository implements LabExperimentRepository {
    private static final RowMapper<LabExperiment> EXPERIMENT_ROW_MAPPER = (resultSet, rowNum) -> new LabExperiment(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getObject("chapter_id", Long.class),
            resultSet.getString("title"),
            resultSet.getString("description"),
            LabExperimentStatus.valueOf(resultSet.getString("status")),
            resultSet.getTimestamp("deadline").toLocalDateTime(),
            resultSet.getInt("max_score"),
            parseAttachmentIds(resultSet.getString("attachment_ids")),
            resultSet.getString("allowed_languages"),
            LabEvaluationMode.valueOf(resultSet.getString("evaluation_mode")),
            resultSet.getBoolean("auto_evaluate"),
            resultSet.getBoolean("report_required"),
            resultSet.getInt("time_limit_ms"),
            resultSet.getInt("memory_limit_kb"),
            resultSet.getLong("created_by"),
            toLocalDateTime(resultSet.getTimestamp("published_at")),
            resultSet.getBoolean("deleted"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(),
            List.of()
    );

    private static final RowMapper<LabTestcase> TESTCASE_ROW_MAPPER = (resultSet, rowNum) -> new LabTestcase(
            resultSet.getLong("id"),
            resultSet.getLong("lab_id"),
            resultSet.getString("input"),
            resultSet.getString("expected_output"),
            resultSet.getInt("score_weight"),
            resultSet.getBoolean("is_public"),
            resultSet.getInt("time_limit_ms"),
            resultSet.getInt("memory_limit_kb"),
            resultSet.getInt("order_num"),
            resultSet.getBoolean("deleted"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabExperimentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabExperiment save(LabExperiment experiment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO lab_experiment
                    (course_id, chapter_id, title, description, status, deadline, max_score, attachment_ids,
                    allowed_languages, evaluation_mode, auto_evaluate, report_required, time_limit_ms,
                     memory_limit_kb, created_by, published_at, deleted, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bindExperiment(statement, experiment);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        replaceTestcases(id, experiment.testcases(), experiment.createdAt());
        return findById(id).orElseThrow(() -> new IllegalStateException("保存实验后无法读取记录"));
    }

    @Override
    public LabExperiment update(LabExperiment experiment) {
        updateExperimentRow(experiment);
        replaceTestcases(experiment.id(), experiment.testcases(), experiment.updatedAt());
        return findById(experiment.id()).orElseThrow(() -> new IllegalStateException("更新实验后无法读取记录"));
    }

    @Override
    public LabExperiment updateLifecycle(LabExperiment experiment) {
        updateExperimentRow(experiment);
        return findById(experiment.id()).orElseThrow(() -> new IllegalStateException("更新实验后无法读取记录"));
    }

    @Override
    public Optional<LabExperiment> findById(long labId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, chapter_id, title, description, status, deadline, max_score,
                               attachment_ids, allowed_languages, evaluation_mode, auto_evaluate, report_required,
                               time_limit_ms, memory_limit_kb, created_by, published_at, deleted, created_at, updated_at
                        FROM lab_experiment
                        WHERE id = ?
                        """,
                EXPERIMENT_ROW_MAPPER,
                labId
        ).stream().findFirst().map(this::attachTestcases);
    }

    @Override
    public List<LabExperiment> findByCourseId(long courseId, LabExperimentStatus status) {
        String sql = """
                SELECT id, course_id, chapter_id, title, description, status, deadline, max_score,
                       attachment_ids, allowed_languages, evaluation_mode, auto_evaluate, report_required,
                       time_limit_ms, memory_limit_kb, created_by, published_at, deleted, created_at, updated_at
                FROM lab_experiment
                WHERE course_id = ? AND deleted = FALSE
                """;
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        if (status != null) {
            sql += " AND status = ? ";
            args.add(status.name());
        }
        sql += " ORDER BY updated_at DESC, id DESC ";
        return jdbcTemplate.query(sql, EXPERIMENT_ROW_MAPPER, args.toArray()).stream()
                .map(experiment -> experiment.withId(experiment.id()))
                .toList();
    }

    private LabExperiment attachTestcases(LabExperiment experiment) {
        List<LabTestcase> testcases = jdbcTemplate.query("""
                        SELECT id, lab_id, input, expected_output, score_weight, is_public, time_limit_ms,
                               memory_limit_kb, order_num, deleted, created_at, updated_at
                        FROM lab_testcase
                        WHERE lab_id = ? AND deleted = FALSE
                        ORDER BY order_num ASC, id ASC
                        """,
                TESTCASE_ROW_MAPPER,
                experiment.id()
        );
        return new LabExperiment(
                experiment.id(),
                experiment.courseId(),
                experiment.chapterId(),
                experiment.title(),
                experiment.description(),
                experiment.status(),
                experiment.deadline(),
                experiment.maxScore(),
                experiment.attachmentIds(),
                experiment.allowedLanguages(),
                experiment.evaluationMode(),
                experiment.autoEvaluate(),
                experiment.reportRequired(),
                experiment.timeLimitMs(),
                experiment.memoryLimitKb(),
                experiment.createdBy(),
                experiment.publishedAt(),
                experiment.deleted(),
                experiment.createdAt(),
                experiment.updatedAt(),
                testcases
        );
    }

    private void replaceTestcases(long labId, List<LabTestcase> testcases, LocalDateTime now) {
        jdbcTemplate.update("DELETE FROM lab_testcase WHERE lab_id = ?", labId);
        for (LabTestcase testcase : testcases) {
            insertTestcase(labId, testcase, now);
        }
    }

    protected void insertTestcase(long labId, LabTestcase testcase, LocalDateTime now) {
        jdbcTemplate.update("""
                        INSERT INTO lab_testcase
                        (lab_id, input, expected_output, score_weight, is_public, time_limit_ms,
                         memory_limit_kb, order_num, deleted, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                labId,
                testcase.input(),
                testcase.expectedOutput(),
                testcase.scoreWeight(),
                testcase.isPublic(),
                testcase.timeLimitMs(),
                testcase.memoryLimitKb(),
                testcase.orderNum(),
                testcase.deleted(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    private void updateExperimentRow(LabExperiment experiment) {
        int updated = jdbcTemplate.update("""
                UPDATE lab_experiment
                SET chapter_id = ?,
                    title = ?,
                    description = ?,
                    status = ?,
                    deadline = ?,
                    max_score = ?,
                    attachment_ids = ?,
                    allowed_languages = ?,
                    evaluation_mode = ?,
                    auto_evaluate = ?,
                    report_required = ?,
                    time_limit_ms = ?,
                    memory_limit_kb = ?,
                    published_at = ?,
                    deleted = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                experiment.chapterId(),
                experiment.title(),
                experiment.description(),
                experiment.status().name(),
                Timestamp.valueOf(experiment.deadline()),
                experiment.maxScore(),
                formatAttachmentIds(experiment.attachmentIds()),
                experiment.allowedLanguages(),
                experiment.evaluationMode().name(),
                experiment.autoEvaluate(),
                experiment.reportRequired(),
                experiment.timeLimitMs(),
                experiment.memoryLimitKb(),
                experiment.publishedAt() == null ? null : Timestamp.valueOf(experiment.publishedAt()),
                experiment.deleted(),
                Timestamp.valueOf(experiment.updatedAt()),
                experiment.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("实验不存在");
        }
    }

    private void bindExperiment(PreparedStatement statement, LabExperiment experiment) throws java.sql.SQLException {
        statement.setLong(1, experiment.courseId());
        statement.setObject(2, experiment.chapterId());
        statement.setString(3, experiment.title());
        statement.setString(4, experiment.description());
        statement.setString(5, experiment.status().name());
        statement.setTimestamp(6, Timestamp.valueOf(experiment.deadline()));
        statement.setInt(7, experiment.maxScore());
        statement.setString(8, formatAttachmentIds(experiment.attachmentIds()));
        statement.setString(9, experiment.allowedLanguages());
        statement.setString(10, experiment.evaluationMode().name());
        statement.setBoolean(11, experiment.autoEvaluate());
        statement.setBoolean(12, experiment.reportRequired());
        statement.setInt(13, experiment.timeLimitMs());
        statement.setInt(14, experiment.memoryLimitKb());
        statement.setLong(15, experiment.createdBy());
        statement.setObject(16, experiment.publishedAt() == null ? null : Timestamp.valueOf(experiment.publishedAt()));
        statement.setBoolean(17, experiment.deleted());
        statement.setTimestamp(18, Timestamp.valueOf(experiment.createdAt()));
        statement.setTimestamp(19, Timestamp.valueOf(experiment.updatedAt()));
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String formatAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return null;
        }
        return attachmentIds.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
    }

    private static List<Long> parseAttachmentIds(String attachmentIds) {
        if (attachmentIds == null || attachmentIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(attachmentIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::parseLong)
                .toList();
    }
}
