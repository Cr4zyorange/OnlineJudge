package com.onlinejudge.lrn.repository;

import com.onlinejudge.integration.learning.LearningAssessmentClient;
import com.onlinejudge.integration.learning.LearningCourseClient;
import com.onlinejudge.lrn.domain.LearningTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcLearningTaskRepository {
    private final JdbcTemplate jdbcTemplate;
    private final LearningCourseClient courseClient;
    private final LearningAssessmentClient assessmentClient;

    public JdbcLearningTaskRepository(JdbcTemplate jdbcTemplate, LearningCourseClient courseClient,
                                      LearningAssessmentClient assessmentClient) {
        this.jdbcTemplate = jdbcTemplate; this.courseClient = courseClient; this.assessmentClient = assessmentClient;
    }

    public List<LearningTask> findByUserId(long userId) {
        List<Long> activeCourses = courseClient.findActiveCourseIds(userId);
        Set<Long> active = Set.copyOf(activeCourses);
        List<TaskRow> snapshots = jdbcTemplate.query("""
                SELECT id,user_id,course_id,source_module,source_id,task_type,title,deadline,progress,status,
                       action_url,snapshot_at,created_at,updated_at FROM lrn_learning_task WHERE user_id=?
                """, this::mapSnapshot, userId).stream().filter(row -> active.contains(row.courseId())).toList();
        Map<Long, String> names = courseClient.findCourseNames(activeCourses);
        List<LearningTask> tasks = new ArrayList<>();
        snapshots.forEach(row -> tasks.add(row.toTask(names.get(row.courseId()))));
        courseClient.findResourceTasks(userId).forEach(task -> tasks.add(external(userId, task, names)));
        assessmentClient.findTasks(userId, activeCourses).forEach(task -> tasks.add(external(userId, task, names)));
        return tasks;
    }

    private LearningTask external(long userId, LearningCourseClient.ExternalTask task, Map<Long, String> names) {
        return new LearningTask(task.id(), userId, task.courseId(),
                firstNonBlank(task.courseName(), names.get(task.courseId()), "课程 " + task.courseId()),
                task.sourceModule(), task.sourceId(), task.taskType(), task.title(), task.deadline(), 0,
                "NOT_STARTED", task.actionUrl(), LocalDateTime.now(), task.createdAt(), task.updatedAt());
    }

    private TaskRow mapSnapshot(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new TaskRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("course_id"),
                rs.getString("source_module"), rs.getLong("source_id"), rs.getString("task_type"),
                rs.getString("title"), rs.getObject("deadline", LocalDateTime.class), rs.getInt("progress"),
                rs.getString("status"), rs.getString("action_url"), rs.getObject("snapshot_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "课程";
    }

    private record TaskRow(long id, long userId, long courseId, String sourceModule, long sourceId,
                           String taskType, String title, LocalDateTime deadline, int progress, String status,
                           String actionUrl, LocalDateTime snapshotAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        LearningTask toTask(String courseName) {
            return new LearningTask(id, userId, courseId,
                    courseName == null || courseName.isBlank() ? "课程 " + courseId : courseName,
                    sourceModule, sourceId, taskType, title, deadline, progress, status, actionUrl,
                    snapshotAt, createdAt, updatedAt);
        }
    }
}
