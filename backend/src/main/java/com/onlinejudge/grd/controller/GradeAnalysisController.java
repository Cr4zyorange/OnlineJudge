package com.onlinejudge.grd.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.service.GradeAnalysisResult;
import com.onlinejudge.grd.service.GradeAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GradeAnalysisController {
    private final GradeAnalysisService gradeAnalysisService;

    public GradeAnalysisController(GradeAnalysisService gradeAnalysisService) {
        this.gradeAnalysisService = gradeAnalysisService;
    }

    @GetMapping("/courses/{courseId}/grade-analysis")
    public ApiResponse<GradeAnalysisResult> getCourseGradeAnalysis(
            @PathVariable long courseId,
            @RequestParam(defaultValue = "COURSE_TOTAL") String targetType,
            @RequestParam(required = false) Long gradeItemId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeAnalysisService.analyzeCourseGrades(
                courseId,
                currentUser.id(),
                targetType,
                gradeItemId
        ));
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无课程成绩管理权限");
        }
    }
}
