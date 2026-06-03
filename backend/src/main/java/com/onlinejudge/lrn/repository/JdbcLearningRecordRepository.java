package com.onlinejudge.lrn.repository;

import com.onlinejudge.lrn.domain.LearningRecord;
import com.onlinejudge.lrn.service.LearningRecordCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcLearningRecordRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveCourseMember(long userId, long courseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crs_course_member member
                INNER JOIN crs_course course ON course.id = member.course_id
                WHERE member.user_id = ?
                  AND member.course_id = ?
                  AND member.join_status = 'ACTIVE'
                  AND member.is_deleted = FALSE
                  AND course.is_deleted = FALSE
                """, Integer.class, userId, courseId);
        return count != null && count > 0;
    }

    public int countRecentReports(long userId, LearningRecordCommand command, LocalDateTime since) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_learning_record
                WHERE user_id = ?
                  AND course_id = ?
                  AND source_module = ?
                  AND source_id = ?
                  AND created_at >= ?
                """, Integer.class, userId, command.courseId(), command.sourceModule(), command.sourceId(), since);
        return count == null ? 0 : count;
    }

    public LearningRecord save(long userId, LearningRecordCommand command) {
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_record
                    (user_id, course_id, source_module, source_id, action_type, duration, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                command.courseId(),
                command.sourceModule(),
                command.sourceId(),
                command.actionType(),
                command.durationSeconds(),
                command.startedAt(),
                command.endedAt());
        return findLatest(userId, command).orElseThrow();
    }

    public List<LearningRecord> findByUserSince(long userId, Long courseId, LocalDateTime since) {
        return jdbcTemplate.query("""
                SELECT record.id,
                       record.user_id,
                       record.course_id,
                       course.course_name,
                       record.source_module,
                       record.source_id,
                       record.action_type,
                       record.duration,
                       record.started_at,
                       record.ended_at,
                       record.created_at
                FROM lrn_learning_record record
                INNER JOIN crs_course_member member
                    ON member.course_id = record.course_id
                    AND member.user_id = record.user_id
                    AND member.join_status = 'ACTIVE'
                    AND member.is_deleted = FALSE
                INNER JOIN crs_course course
                    ON course.id = record.course_id
                    AND course.is_deleted = FALSE
                WHERE record.user_id = ?
                  AND (? IS NULL OR record.course_id = ?)
                  AND record.started_at >= ?
                ORDER BY record.started_at DESC, record.id DESC
                """, this::mapRow, userId, courseId, courseId, since);
    }

    private Optional<LearningRecord> findLatest(long userId, LearningRecordCommand command) {
        return jdbcTemplate.query("""
                SELECT record.id,
                       record.user_id,
                       record.course_id,
                       course.course_name,
                       record.source_module,
                       record.source_id,
                       record.action_type,
                       record.duration,
                       record.started_at,
                       record.ended_at,
                       record.created_at
                FROM lrn_learning_record record
                INNER JOIN crs_course course ON course.id = record.course_id
                WHERE record.user_id = ?
                  AND record.course_id = ?
                  AND record.source_module = ?
                  AND record.source_id = ?
                  AND record.action_type = ?
                ORDER BY record.id DESC
                """, this::mapRow,
                userId,
                command.courseId(),
                command.sourceModule(),
                command.sourceId(),
                command.actionType()).stream().findFirst();
    }

    private LearningRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LearningRecord(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("course_id"),
                rs.getString("course_name"),
                rs.getString("source_module"),
                rs.getLong("source_id"),
                rs.getString("action_type"),
                rs.getInt("duration"),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("ended_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
        );
    }
}
