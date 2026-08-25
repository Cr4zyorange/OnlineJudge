package com.onlinejudge.grd.repository;

import com.onlinejudge.grd.domain.GradeAnalysisSourceVersion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

final class JdbcGradeAnalysisSourceVersionStore {
    static final String COURSE_TOTAL = "COURSE_TOTAL";
    static final String GRADE_ITEM = "GRADE_ITEM";
    private static final long COURSE_TOTAL_ITEM_KEY = 0L;

    private final JdbcTemplate jdbcTemplate;

    JdbcGradeAnalysisSourceVersionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    GradeAnalysisSourceVersion findCourseTotal(long courseId) {
        return find(courseId, COURSE_TOTAL, COURSE_TOTAL_ITEM_KEY);
    }

    GradeAnalysisSourceVersion findGradeItem(long courseId, long gradeItemId) {
        return find(courseId, GRADE_ITEM, gradeItemId);
    }

    void bumpCourseTotal(long courseId, LocalDateTime sourceDataTime) {
        bump(courseId, COURSE_TOTAL, COURSE_TOTAL_ITEM_KEY, sourceDataTime);
    }

    void bumpGradeItem(long courseId, long gradeItemId, LocalDateTime sourceDataTime) {
        bump(courseId, GRADE_ITEM, gradeItemId, sourceDataTime);
    }

    private GradeAnalysisSourceVersion find(long courseId, String targetType, long gradeItemKey) {
        return jdbcTemplate.query("""
                        SELECT source_version, source_data_time
                          FROM t_grade_analysis_source_version
                         WHERE course_id = ?
                           AND target_type = ?
                           AND grade_item_key = ?
                        """,
                (resultSet, rowNum) -> new GradeAnalysisSourceVersion(
                        resultSet.getLong("source_version"),
                        resultSet.getTimestamp("source_data_time") == null
                                ? null
                                : resultSet.getTimestamp("source_data_time").toLocalDateTime()
                ),
                courseId,
                targetType,
                gradeItemKey
        ).stream().findFirst().orElseGet(GradeAnalysisSourceVersion::initial);
    }

    private void bump(long courseId, String targetType, long gradeItemKey, LocalDateTime sourceDataTime) {
        LocalDateTime effectiveSourceTime = sourceDataTime == null ? LocalDateTime.now() : sourceDataTime;
        LocalDateTime now = LocalDateTime.now();
        if (updateExisting(courseId, targetType, gradeItemKey, effectiveSourceTime, now) > 0) {
            return;
        }
        try {
            jdbcTemplate.update("""
                            INSERT INTO t_grade_analysis_source_version
                            (course_id, target_type, grade_item_key, source_version, source_data_time, updated_at)
                            VALUES (?, ?, ?, 1, ?, ?)
                            """,
                    courseId,
                    targetType,
                    gradeItemKey,
                    Timestamp.valueOf(effectiveSourceTime),
                    Timestamp.valueOf(now)
            );
        } catch (DuplicateKeyException concurrentInsert) {
            updateExisting(courseId, targetType, gradeItemKey, effectiveSourceTime, now);
        }
    }

    private int updateExisting(
            long courseId,
            String targetType,
            long gradeItemKey,
            LocalDateTime sourceDataTime,
            LocalDateTime updatedAt
    ) {
        Timestamp sourceTimestamp = Timestamp.valueOf(sourceDataTime);
        return jdbcTemplate.update("""
                        UPDATE t_grade_analysis_source_version
                           SET source_version = source_version + 1,
                               source_data_time = CASE
                                   WHEN source_data_time IS NULL OR source_data_time < ? THEN ?
                                   ELSE source_data_time
                               END,
                               updated_at = ?
                         WHERE course_id = ?
                           AND target_type = ?
                           AND grade_item_key = ?
                        """,
                sourceTimestamp,
                sourceTimestamp,
                Timestamp.valueOf(updatedAt),
                courseId,
                targetType,
                gradeItemKey
        );
    }
}
