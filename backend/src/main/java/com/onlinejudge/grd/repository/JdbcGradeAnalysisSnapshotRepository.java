package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeAnalysisSnapshot;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshotRepository;
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
public class JdbcGradeAnalysisSnapshotRepository implements GradeAnalysisSnapshotRepository {
    private static final RowMapper<GradeAnalysisSnapshot> ROW_MAPPER = (resultSet, rowNum) -> new GradeAnalysisSnapshot(
            resultSet.getLong("id"),
            resultSet.getLong("course_id"),
            resultSet.getString("target_type"),
            resultSet.getObject("grade_item_id", Long.class),
            resultSet.getTimestamp("source_data_time").toLocalDateTime(),
            resultSet.getString("source_fingerprint"),
            resultSet.getBigDecimal("average_score"),
            resultSet.getBigDecimal("max_score"),
            resultSet.getBigDecimal("min_score"),
            resultSet.getBigDecimal("pass_rate"),
            resultSet.getBigDecimal("completion_rate"),
            resultSet.getObject("total_student_count", Integer.class),
            resultSet.getObject("completed_count", Integer.class),
            resultSet.getObject("missing_count", Integer.class),
            resultSet.getObject("unsubmitted_count", Integer.class),
            resultSet.getObject("ungraded_count", Integer.class),
            resultSet.getString("distribution_json"),
            resultSet.getLong("generated_by"),
            resultSet.getTimestamp("generated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGradeAnalysisSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GradeAnalysisSnapshot save(GradeAnalysisSnapshot snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_grade_analysis_snapshot
                    (course_id, target_type, grade_item_id, source_data_time, source_fingerprint,
                     average_score, max_score, min_score, pass_rate, completion_rate,
                     total_student_count, completed_count, missing_count, unsubmitted_count, ungraded_count,
                     distribution_json, generated_by, generated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, snapshot.courseId());
            statement.setString(2, snapshot.targetType());
            statement.setObject(3, snapshot.gradeItemId());
            statement.setTimestamp(4, Timestamp.valueOf(snapshot.sourceDataTime()));
            statement.setString(5, snapshot.sourceFingerprint());
            statement.setBigDecimal(6, snapshot.averageScore());
            statement.setBigDecimal(7, snapshot.maxScore());
            statement.setBigDecimal(8, snapshot.minScore());
            statement.setBigDecimal(9, snapshot.passRate());
            statement.setBigDecimal(10, snapshot.completionRate());
            statement.setObject(11, snapshot.totalStudentCount());
            statement.setObject(12, snapshot.completedCount());
            statement.setObject(13, snapshot.missingCount());
            statement.setObject(14, snapshot.unsubmittedCount());
            statement.setObject(15, snapshot.ungradedCount());
            statement.setString(16, snapshot.distributionJson());
            statement.setLong(17, snapshot.generatedBy());
            statement.setTimestamp(18, Timestamp.valueOf(snapshot.generatedAt()));
            return statement;
        }, keyHolder);
        return snapshot.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public Optional<GradeAnalysisSnapshot> findLatest(long courseId, String targetType, Long gradeItemId) {
        return jdbcTemplate.query("""
                        SELECT id, course_id, target_type, grade_item_id, source_data_time, source_fingerprint,
                               average_score, max_score, min_score, pass_rate, completion_rate,
                               total_student_count, completed_count, missing_count, unsubmitted_count, ungraded_count,
                               distribution_json, generated_by, generated_at
                        FROM t_grade_analysis_snapshot
                        WHERE course_id = ?
                          AND target_type = ?
                          AND ((? IS NULL AND grade_item_id IS NULL) OR grade_item_id = ?)
                        ORDER BY generated_at DESC, id DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                courseId,
                targetType,
                gradeItemId,
                gradeItemId
        ).stream().findFirst();
    }
}
