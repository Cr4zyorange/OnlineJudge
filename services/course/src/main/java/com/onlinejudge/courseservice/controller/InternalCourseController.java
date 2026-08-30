package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v2/courses")
public class InternalCourseController {
    private final CourseService service;
    public InternalCourseController(CourseService service) { this.service = service; }

    @GetMapping("/{courseId}/authorizations/{userId}")
    public CourseService.AuthorizationDecision authorization(@PathVariable long courseId, @PathVariable long userId,
                                                             @RequestParam String action) {
        return service.authorization(courseId, userId, action);
    }

    @GetMapping("/{courseId}/members")
    public CourseService.MemberPage members(@PathVariable long courseId, @RequestParam(required = false) String role,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.memberPage(courseId, role, status, page, size);
    }
}
