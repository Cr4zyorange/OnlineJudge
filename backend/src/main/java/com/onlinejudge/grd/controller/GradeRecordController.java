package com.onlinejudge.grd.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.service.CourseGradeRow;
import com.onlinejudge.grd.service.GradeRecalculationResult;
import com.onlinejudge.grd.service.GradeRecordService;
import com.onlinejudge.grd.service.GradeSyncResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class GradeRecordController {
    private final GradeRecordService gradeRecordService;

    public GradeRecordController(GradeRecordService gradeRecordService) {
        this.gradeRecordService = gradeRecordService;
    }

    @PostMapping("/courses/{courseId}/grades/sync")
    public ApiResponse<GradeSyncResult> syncSourceGrades(
            @PathVariable long courseId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeRecordService.syncSourceGrades(courseId, currentUser.id()));
    }

    @PostMapping("/courses/{courseId}/grades/recalculate")
    public ApiResponse<GradeRecalculationResult> recalculateCourseGrades(
            @PathVariable long courseId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeRecordService.recalculateCourseGrades(courseId, currentUser.id()));
    }

    @GetMapping("/courses/{courseId}/grades")
    public ApiResponse<List<CourseGradeRow>> listCourseGrades(
            @PathVariable long courseId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeRecordService.listCourseGrades(courseId, currentUser.id()));
    }

    @GetMapping("/courses/{courseId}/grades/students/{studentId}")
    public ApiResponse<CourseGradeRow> getStudentGradeDetail(
            @PathVariable long courseId,
            @PathVariable long studentId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeRecordService.getStudentGradeDetail(courseId, studentId, currentUser.id()));
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无课程成绩管理权限");
        }
    }
}
