package com.onlinejudge.lrn.repository;

import com.onlinejudge.lrn.domain.LearningTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcLearningTaskRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LearningTask> findByUserId(long userId) {
        return jdbcTemplate.query("""
                SELECT task.id,
                       task.user_id,
                       task.course_id,
                       course.course_name,
                       task.source_module,
                       task.source_id,
                       task.task_type,
                       task.title,
                       task.deadline,
                       task.progress,
                       task.status,
                       task.action_url,
                       task.snapshot_at,
                       task.created_at,
                       task.updated_at
                FROM lrn_learning_task task
                LEFT JOIN crs_course course ON course.id = task.course_id
                WHERE task.user_id = ?
                """, this::mapRow, userId);
    }

    private LearningTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        long courseId = rs.getLong("course_id");
        String courseName = rs.getString("course_name");
        return new LearningTask(
                rs.getLong("id"),
                rs.getLong("user_id"),
                courseId,
                courseName == null || courseName.isBlank() ? "课程 " + courseId : courseName,
                rs.getString("source_module"),
                rs.getLong("source_id"),
                rs.getString("task_type"),
                rs.getString("title"),
                nullableDateTime(rs, "deadline"),
                rs.getInt("progress"),
                rs.getString("status"),
                rs.getString("action_url"),
                nullableDateTime(rs, "snapshot_at"),
                nullableDateTime(rs, "created_at"),
                nullableDateTime(rs, "updated_at")
        );
    }

    private LocalDateTime nullableDateTime(ResultSet rs, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
