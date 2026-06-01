package com.onlinejudge.lrn.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.lrn.service.LearningCourseProgressAggregate;
import com.onlinejudge.lrn.service.LearningProgressItem;
import com.onlinejudge.lrn.service.LearningProgressOverview;
import com.onlinejudge.lrn.service.LearningProgressSaveRequest;
import com.onlinejudge.lrn.service.LearningProgressService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningProgressController {
    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @GetMapping("/progress")
    public ApiResponse<LearningProgressOverview> listProgress(
            CurrentUser currentUser,
            @RequestParam(required = false) Long courseId
    ) {
        return ApiResponse.ok(learningProgressService.listProgress(currentUser.id(), courseId));
    }

    @GetMapping("/progress/teacher")
    public ApiResponse<LearningCourseProgressAggregate> teacherProgress(
            CurrentUser currentUser,
            @RequestParam Long courseId
    ) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("无权查看课程学习进度统计");
        }
        return ApiResponse.ok(learningProgressService.listTeacherCourseProgress(currentUser.id(), courseId));
    }

    @PostMapping("/progress")
    public ApiResponse<LearningProgressItem> saveProgress(
            CurrentUser currentUser,
            @RequestBody LearningProgressSaveRequest request
    ) {
        return ApiResponse.ok(learningProgressService.saveProgress(currentUser.id(), request));
    }
}
