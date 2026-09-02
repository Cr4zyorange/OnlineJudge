package com.onlinejudge.courseservice.learning;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Course-owned learning task queries and homework/lab fact projection (#355). */
@Service
public class LrnTaskService {
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final LrnTaskRepository tasks;

    public LrnTaskService(LrnTaskRepository tasks) { this.tasks = tasks; }

    public List<RecentTask> recentTasks(long courseId, long userId, int limit) {
        return tasks.recent(courseId, userId, limit).stream()
                .map(row -> new RecentTask(row.id(), row.taskType(), row.title(), row.courseId(), row.courseName(),
                        format(row.deadline()), row.progress(), row.status(), row.actionUrl()))
                .toList();
    }

    public LearningTaskPage listTasks(long userId, String taskType, String status, Long courseId,
                                      String sortBy, String order, Integer page, Integer size) {
        int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        List<String> taskTypes = taskType == null || taskType.isBlank()
                ? List.of()
                : Arrays.stream(taskType.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        long total = tasks.count(userId, taskTypes, status, courseId);
        return new LearningTaskPage(
                tasks.list(userId, taskTypes, status, courseId, sortBy, order, normalizedPage, normalizedSize).stream()
                        .map(row -> new LearningTaskSummary(row.id(), row.taskType(), row.title(), row.courseId(),
                                row.courseName(), format(row.deadline()), row.progress(), row.status(), row.actionUrl()))
                        .toList(),
                total, normalizedPage, normalizedSize);
    }

    /**
     * Applies a frozen homework/lab published fact to every active student,
     * guarded by the source row itself so a replayed event never duplicates a
     * task.  Returns the resolved receiver roster for the parallel notification
     * projection.
     */
    public List<Long> applyPublishedFact(long courseId, String sourceModule, String taskType, String title,
                                         LocalDateTime deadline, String actionUrl, long sourceId,
                                         List<Long> receiverUserIds) {
        for (Long studentId : receiverUserIds) {
            if (!tasks.exists(studentId, courseId, sourceModule, sourceId)) {
                tasks.insert(studentId, courseId, sourceModule, sourceId, taskType, title, deadline, actionUrl);
            }
        }
        return receiverUserIds;
    }

    public void reflectProgress(long userId, long courseId, String sourceModule, long sourceId, int percent) {
        String status = percent >= 100 ? "COMPLETED" : (percent > 0 ? "IN_PROGRESS" : "NOT_STARTED");
        tasks.updateProgress(userId, courseId, sourceModule, sourceId, percent, status);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }

    public record RecentTask(long taskId, String taskType, String title, long courseId, String courseName,
                             String deadline, int progress, String status, String actionUrl) { }

    public record LearningTaskSummary(long taskId, String taskType, String title, long courseId, String courseName,
                                      String deadline, int progress, String status, String actionUrl) { }

    public record LearningTaskPage(List<LearningTaskSummary> records, long total, int page, int size) { }
}
