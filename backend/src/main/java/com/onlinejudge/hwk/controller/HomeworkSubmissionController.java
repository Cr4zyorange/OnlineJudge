package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
public class HomeworkSubmissionController {
    private final HomeworkSubmissionService homeworkSubmissionService;

    public HomeworkSubmissionController(HomeworkSubmissionService homeworkSubmissionService) {
        this.homeworkSubmissionService = homeworkSubmissionService;
    }

    @GetMapping("/{submissionId}")
    public ApiResponse<HomeworkSubmissionResponse> detail(
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        com.onlinejudge.hwk.service.HomeworkSubmissionService.SubmissionDetail detail = homeworkSubmissionService.detail(
                submissionId,
                currentUser.id()
        );
        if (detail.managerView()) {
            return ApiResponse.ok(HomeworkSubmissionResponse.fromTeacherView(detail.submission()));
        }
        return ApiResponse.ok(HomeworkSubmissionResponse.fromStudentView(detail.homework(), detail.submission()));
    }

    @GetMapping("/{submissionId}/evaluation")
    public ApiResponse<HomeworkEvaluationResponse> evaluation(
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        HomeworkSubmissionService.EvaluationDetail detail = homeworkSubmissionService.evaluationDetail(
                submissionId,
                currentUser.id()
        );
        return ApiResponse.ok(HomeworkEvaluationResponse.from(detail));
    }

    @PostMapping("/{submissionId}/reevaluate")
    public ApiResponse<HomeworkEvaluationResponse> reevaluate(
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        HomeworkSubmissionService.EvaluationDetail detail = homeworkSubmissionService.reevaluate(
                submissionId,
                currentUser.id()
        );
        return ApiResponse.ok(HomeworkEvaluationResponse.from(detail));
    }
}
