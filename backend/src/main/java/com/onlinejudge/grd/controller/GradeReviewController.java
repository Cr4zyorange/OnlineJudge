package com.onlinejudge.grd.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.domain.GradeReviewStatus;
import com.onlinejudge.grd.service.GradeReviewProcessResult;
import com.onlinejudge.grd.service.GradeReviewRequestPage;
import com.onlinejudge.grd.service.GradeReviewService;
import com.onlinejudge.grd.service.GradeReviewSubmissionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GradeReviewController {
    private final GradeReviewService gradeReviewService;

    public GradeReviewController(GradeReviewService gradeReviewService) {
        this.gradeReviewService = gradeReviewService;
    }

    @PostMapping("/courses/{courseId}/grade-review-requests")
    public ApiResponse<GradeReviewSubmissionResult> submitReviewRequest(
            @PathVariable long courseId,
            @RequestBody SubmitGradeReviewRequest request,
            CurrentUser currentUser
    ) {
        requireStudent(currentUser);
        return ApiResponse.ok(gradeReviewService.submitReviewRequest(courseId, currentUser.id(), request.toCommand()));
    }

    @GetMapping("/courses/{courseId}/my-grade-review-requests")
    public ApiResponse<GradeReviewRequestPage> listMyReviewRequests(
            @PathVariable long courseId,
            @RequestParam(required = false) GradeReviewStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            CurrentUser currentUser
    ) {
        requireStudent(currentUser);
        return ApiResponse.ok(gradeReviewService.listMyReviewRequests(courseId, currentUser.id(), status, page, size));
    }

    @GetMapping("/courses/{courseId}/grade-review-requests")
    public ApiResponse<GradeReviewRequestPage> listCourseReviewRequests(
            @PathVariable long courseId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long gradeItemId,
            @RequestParam(required = false) GradeReviewStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeReviewService.listCourseReviewRequests(
                courseId,
                currentUser.id(),
                studentId,
                gradeItemId,
                status,
                page,
                size
        ));
    }

    @PutMapping("/grade-review-requests/{requestId}/process")
    public ApiResponse<GradeReviewProcessResult> processReviewRequest(
            @PathVariable long requestId,
            @RequestBody ProcessGradeReviewRequest request,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeReviewService.processReviewRequest(requestId, currentUser.id(), request.toCommand()));
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无权限处理课程成绩异议申请");
        }
    }

    private void requireStudent(CurrentUser currentUser) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("学生无课程成绩复核权限");
        }
    }
}
