package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluations")
public class HomeworkEvaluationController {
    private final HomeworkSubmissionService homeworkSubmissionService;

    public HomeworkEvaluationController(HomeworkSubmissionService homeworkSubmissionService) {
        this.homeworkSubmissionService = homeworkSubmissionService;
    }

    @GetMapping("/{evaluationId}/logs")
    public ApiResponse<HomeworkEvaluationResponse> logs(
            @PathVariable long evaluationId,
            CurrentUser currentUser
    ) {
        HomeworkSubmissionService.EvaluationDetail detail = homeworkSubmissionService.evaluationLogs(
                evaluationId,
                currentUser.id()
        );
        return ApiResponse.ok(HomeworkEvaluationResponse.from(detail));
    }
}
