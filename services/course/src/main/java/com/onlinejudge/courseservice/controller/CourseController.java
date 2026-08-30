package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.service.CourseService;
import com.onlinejudge.courseservice.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseService.CourseView> create(@Valid @RequestBody CreateCourseRequest request,
                                                         @RequestAttribute("course.currentUser") CurrentUser user,
                                                         HttpServletRequest servletRequest) {
        return ApiResponse.created(service.create(request.name(), request.enrollmentMode(), user, requestId(servletRequest)));
    }

    @GetMapping
    public ApiResponse<List<CourseService.CourseView>> list(@RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.list(user));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseService.CourseView> detail(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.detail(courseId, user));
    }

    @PostMapping("/{courseId}/join")
    public ApiResponse<CourseService.MemberView> join(@PathVariable long courseId,
                                                       @RequestAttribute("course.currentUser") CurrentUser user,
                                                       HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.join(courseId, user, requestId(servletRequest)));
    }

    @GetMapping("/{courseId}/members")
    public ApiResponse<CourseService.MemberPage> members(@PathVariable long courseId,
                                                          @RequestParam(required = false) String role,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size,
                                                          @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.members(courseId, role, page, size, user));
    }

    @PutMapping("/{courseId}/members/{userId}")
    public ApiResponse<CourseService.MemberView> changeMember(@PathVariable long courseId, @PathVariable long userId,
                                                               @RequestBody MemberChangeRequest request,
                                                               @RequestAttribute("course.currentUser") CurrentUser user,
                                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.changeMember(courseId, userId, request.role(), request.status(), user, requestId(servletRequest)));
    }

    @DeleteMapping("/{courseId}/members/{userId}")
    public ApiResponse<CourseService.MemberView> removeMember(@PathVariable long courseId, @PathVariable long userId,
                                                               @RequestAttribute("course.currentUser") CurrentUser user,
                                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.changeMember(courseId, userId, null, "REMOVED", user, requestId(servletRequest)));
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<CourseService.ChapterView> chapter(@PathVariable long courseId, @Valid @RequestBody ChapterRequest request,
                                                          @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.createChapter(courseId, request.title(), request.parentId(), user));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<CourseService.ChapterView>> chapters(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.chapters(courseId, user));
    }

    @PostMapping("/{courseId}/resources")
    public ApiResponse<CourseService.ResourceView> resource(@PathVariable long courseId, @Valid @RequestBody ResourceRequest request,
                                                            @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.createResource(courseId, request.title(), request.url(), user));
    }

    @GetMapping("/{courseId}/resources")
    public ApiResponse<List<CourseService.ResourceView>> resources(@PathVariable long courseId, @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(service.resources(courseId, user));
    }

    private String requestId(HttpServletRequest request) { return request.getAttribute("course.requestId").toString(); }
    public record CreateCourseRequest(@NotBlank String name, String enrollmentMode) { }
    public record MemberChangeRequest(String role, String status) { }
    public record ChapterRequest(@NotBlank String title, String parentId) { }
    public record ResourceRequest(@NotBlank String title, @NotBlank String url) { }
}
