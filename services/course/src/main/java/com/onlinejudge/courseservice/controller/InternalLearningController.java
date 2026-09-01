package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Course's LRN role exposes bounded recent tasks to trusted workloads. */
@RestController
@RequestMapping("/internal/v2/learning")
public class InternalLearningController {
    private final LrnTaskService tasks;

    public InternalLearningController(LrnTaskService tasks) { this.tasks = tasks; }

    @GetMapping("/tasks/recent")
    public RecentTaskPage recent(@RequestParam long courseId, @RequestParam long userId,
                                 @RequestParam(defaultValue = "5") int limit) {
        int size = Math.max(1, Math.min(5, limit));
        List<LrnTaskService.RecentTask> items = tasks.recentTasks(courseId, userId, size);
        List<RecentTask> pageItems = items.stream()
                .map(task -> new RecentTask(task.taskId(), task.taskType(), task.title(), task.courseId(),
                        task.courseName(), task.deadline(), task.progress(), task.status(), task.actionUrl()))
                .toList();
        return new RecentTaskPage(pageItems, 0, size, pageItems.size());
    }

    public record RecentTaskPage(List<RecentTask> items, int page, int size, long total) { }

    public record RecentTask(long taskId, String taskType, String title, long courseId, String courseName,
                             String deadline, int progress, String status, String actionUrl) { }
}
