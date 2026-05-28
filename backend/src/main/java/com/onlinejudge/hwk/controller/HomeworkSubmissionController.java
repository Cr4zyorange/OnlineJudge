package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
public class HomeworkSubmissionController {
    private final HomeworkSubmissionService homeworkSubmissionService;
    private final com.onlinejudge.hwk.service.HomeworkEvaluationService homeworkEvaluationService;

    public HomeworkSubmissionController(
            HomeworkSubmissionService homeworkSubmissionService,
            com.onlinejudge.hwk.service.HomeworkEvaluationService homeworkEvaluationService
    ) {
        this.homeworkSubmissionService = homeworkSubmissionService;
        this.homeworkEvaluationService = homeworkEvaluationService;
    }

    @GetMapping("/{submissionId}")
    public ApiResponse<HomeworkSubmissionResponse> get(@PathVariable long submissionId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkSubmissionResponse.from(homeworkSubmissionService.get(submissionId, currentUser)));
    }

    @GetMapping("/{submissionId}/evaluation")
    public ApiResponse<HomeworkEvaluationResponse> evaluation(@PathVariable long submissionId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkEvaluationResponse.from(
                homeworkEvaluationService.getForSubmission(submissionId, currentUser)
        ));
    }

    @org.springframework.web.bind.annotation.PostMapping("/{submissionId}/reevaluate")
    public ApiResponse<HomeworkEvaluationResponse> reevaluate(@PathVariable long submissionId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkEvaluationResponse.from(
                homeworkEvaluationService.reevaluate(submissionId, currentUser)
        ));
    }
}
