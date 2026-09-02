package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnProgressService;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LrnProgressController {
    private final LrnProgressService progress;

    public LrnProgressController(LrnProgressService progress) { this.progress = progress; }

    @GetMapping("/progress")
    public ApiResponse<LrnProgressService.LearningProgressOverview> list(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(progress.overview(user.id(), courseId));
    }

    @GetMapping("/progress/teacher")
    public ApiResponse<LrnProgressService.LearningCourseProgressAggregate> teacher(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestParam Long courseId) {
        return ApiResponse.ok(progress.teacherOverview(user, courseId));
    }

    @PostMapping("/progress")
    public ApiResponse<LrnProgressService.LearningProgressItem> save(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestBody LrnProgressService.LearningProgressSaveRequest request) {
        return ApiResponse.ok(progress.save(user.id(), request));
    }
}
