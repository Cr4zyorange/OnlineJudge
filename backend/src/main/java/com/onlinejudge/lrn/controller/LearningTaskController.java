package com.onlinejudge.lrn.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.lrn.service.LearningTaskPage;
import com.onlinejudge.lrn.service.LearningTaskQuery;
import com.onlinejudge.lrn.service.LearningTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningTaskController {
    private final LearningTaskService learningTaskService;

    public LearningTaskController(LearningTaskService learningTaskService) {
        this.learningTaskService = learningTaskService;
    }

    @GetMapping("/tasks")
    public ApiResponse<LearningTaskPage> listTasks(
            CurrentUser currentUser,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(learningTaskService.listTasks(
                currentUser.id(),
                new LearningTaskQuery(taskType, status, courseId, sortBy, order, page, size)
        ));
    }
}
