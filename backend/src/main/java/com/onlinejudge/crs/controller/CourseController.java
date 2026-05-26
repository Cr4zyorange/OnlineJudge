package com.onlinejudge.crs.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CoursePermissionResponse;
import com.onlinejudge.crs.domain.dto.CourseResponse;
import com.onlinejudge.crs.domain.dto.CourseUpdateRequest;
import com.onlinejudge.crs.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ApiResponse<CourseResponse> create(@Valid @RequestBody CourseCreateRequest request, CurrentUser currentUser) {
        return ApiResponse.ok(courseService.create(request, currentUser));
    }

    @GetMapping
    public ApiResponse<PageResponse<CourseResponse>> list(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "all") String scope,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          CurrentUser currentUser) {
        return ApiResponse.ok(courseService.list(keyword, scope, page, size, currentUser));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> detail(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(courseService.detail(courseId, currentUser));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(@PathVariable Long courseId,
                                              @Valid @RequestBody CourseUpdateRequest request,
                                              CurrentUser currentUser) {
        return ApiResponse.ok(courseService.update(courseId, request, currentUser));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> archive(@PathVariable Long courseId, CurrentUser currentUser) {
        courseService.archive(courseId, currentUser);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{courseId}/join")
    public ApiResponse<CoursePermissionResponse> join(@PathVariable Long courseId, CurrentUser currentUser) {
        return ApiResponse.ok(courseService.join(courseId, currentUser));
    }

    @GetMapping("/{courseId}/permissions/{userId}")
    public ApiResponse<CoursePermissionResponse> permission(@PathVariable Long courseId, @PathVariable Long userId) {
        return ApiResponse.ok(courseService.permission(courseId, userId));
    }
}
