package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnTaskService;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LrnTaskController {
    private final LrnTaskService tasks;

    public LrnTaskController(LrnTaskService tasks) { this.tasks = tasks; }

    @GetMapping("/tasks")
    public ApiResponse<LrnTaskService.LearningTaskPage> listTasks(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(tasks.listTasks(user.id(), taskType, status, courseId, sortBy, order, page, size));
    }
}
