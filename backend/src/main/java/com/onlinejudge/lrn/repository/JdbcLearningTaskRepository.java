package com.onlinejudge.lrn.repository;

import com.onlinejudge.lrn.domain.LearningTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcLearningTaskRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LearningTask> findByUserId(long userId) {
        List<LearningTask> tasks = new ArrayList<>();
        tasks.addAll(findSnapshotTasks(userId));
        tasks.addAll(findCourseResourceTasks(userId));
        tasks.addAll(findLabExperimentTasks(userId));
        tasks.addAll(findHomeworkTasks(userId));
        return tasks;
    }

    private List<LearningTask> findSnapshotTasks(long userId) {
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

    private List<LearningTask> findCourseResourceTasks(long userId) {
        return jdbcTemplate.query("""
                SELECT resource.id + 1000000000 AS id,
                       ? AS user_id,
                       resource.course_id,
                       course.course_name,
                       'CRS' AS source_module,
                       resource.id AS source_id,
                       'RESOURCE' AS task_type,
                       resource.resource_name AS title,
                       NULL AS deadline,
                       0 AS progress,
                       'NOT_STARTED' AS status,
                       CASE
                           WHEN resource.external_url IS NOT NULL AND resource.external_url <> ''
                               THEN resource.external_url
                           ELSE CONCAT('/courses/', resource.course_id, '/resources/', resource.id)
                       END AS action_url,
                       CURRENT_TIMESTAMP AS snapshot_at,
                       resource.created_at,
                       resource.updated_at
                FROM crs_resource resource
                INNER JOIN crs_course course ON course.id = resource.course_id
                INNER JOIN crs_course_member member
                    ON member.course_id = resource.course_id
                    AND member.user_id = ?
                    AND member.join_status = 'ACTIVE'
                    AND member.is_deleted = FALSE
                WHERE resource.is_deleted = 0
                  AND course.is_deleted = FALSE
                """, this::mapRow, userId, userId);
    }

    private List<LearningTask> findLabExperimentTasks(long userId) {
        return jdbcTemplate.query("""
                SELECT experiment.id + 2000000000 AS id,
                       ? AS user_id,
                       experiment.course_id,
                       course.course_name,
                       'LAB' AS source_module,
                       experiment.id AS source_id,
                       'EXPERIMENT' AS task_type,
                       experiment.title,
                       experiment.deadline,
                       0 AS progress,
                       'NOT_STARTED' AS status,
                       CONCAT('/courses/', experiment.course_id, '/labs/', experiment.id) AS action_url,
                       CURRENT_TIMESTAMP AS snapshot_at,
                       experiment.created_at,
                       experiment.updated_at
                FROM lab_experiment experiment
                INNER JOIN crs_course course ON course.id = experiment.course_id
                INNER JOIN crs_course_member member
                    ON member.course_id = experiment.course_id
                    AND member.user_id = ?
                    AND member.join_status = 'ACTIVE'
                    AND member.is_deleted = FALSE
                WHERE experiment.deleted = 0
                  AND experiment.status IN ('PUBLISHED', 'CLOSED', 'SCORE_PUBLISHED')
                  AND course.is_deleted = FALSE
                """, this::mapRow, userId, userId);
    }

    private List<LearningTask> findHomeworkTasks(long userId) {
        return jdbcTemplate.query("""
                SELECT homework.id + 3000000000 AS id,
                       ? AS user_id,
                       homework.course_id,
                       course.course_name,
                       'HWK' AS source_module,
                       homework.id AS source_id,
                       'HOMEWORK' AS task_type,
                       homework.title,
                       homework.deadline,
                       0 AS progress,
                       'NOT_STARTED' AS status,
                       CONCAT('/courses/', homework.course_id, '/homeworks/', homework.id) AS action_url,
                       CURRENT_TIMESTAMP AS snapshot_at,
                       homework.created_at,
                       homework.updated_at
                FROM t_hwk_homework homework
                INNER JOIN crs_course course ON course.id = homework.course_id
                INNER JOIN crs_course_member member
                    ON member.course_id = homework.course_id
                    AND member.user_id = ?
                    AND member.join_status = 'ACTIVE'
                    AND member.is_deleted = FALSE
                WHERE homework.is_deleted = 0
                  AND homework.status IN ('PUBLISHED', 'CLOSED', 'SCORE_PUBLISHED')
                  AND course.is_deleted = FALSE
                """, this::mapRow, userId, userId);
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
