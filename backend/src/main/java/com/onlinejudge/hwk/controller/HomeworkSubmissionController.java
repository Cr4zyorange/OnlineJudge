package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

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

    @PutMapping("/{submissionId}/review")
    public ApiResponse<HomeworkSubmissionResponse> review(
            @PathVariable long submissionId,
            CurrentUser currentUser,
            @RequestBody(required = false) HomeworkReviewRequest request
    ) {
        HomeworkSubmissionService.SubmissionDetail detail = homeworkSubmissionService.review(
                submissionId,
                currentUser.id(),
                request == null ? null : request.toCommand()
        );
        return ApiResponse.ok(HomeworkSubmissionResponse.fromTeacherView(detail.submission()));
    }

    @GetMapping("/{submissionId}/review-logs")
    public ApiResponse<List<HomeworkReviewLogResponse>> reviewLogs(
            @PathVariable long submissionId,
            CurrentUser currentUser
    ) {
        HomeworkSubmissionService.ReviewLogDetail detail = homeworkSubmissionService.reviewLogs(
                submissionId,
                currentUser.id()
        );
        return ApiResponse.ok(detail.logs().stream().map(HomeworkReviewLogResponse::from).toList());
    }

    @PostMapping("/{submissionId}/reevaluate")
    public ApiResponse<HomeworkEvaluationResponse> reevaluate(
            @PathVariable long submissionId,
            CurrentUser currentUser,
            @RequestBody(required = false) HomeworkReevaluationRequest request
    ) {
        HomeworkSubmissionService.EvaluationDetail detail = homeworkSubmissionService.reevaluate(
                submissionId,
                currentUser.id(),
                request == null ? null : request.reason()
        );
        return ApiResponse.ok(HomeworkEvaluationResponse.from(detail));
    }

    public record HomeworkReevaluationRequest(String reason) {
    }

    public record HomeworkReviewRequest(BigDecimal manualScore, BigDecimal finalScore, String comment) {
        HomeworkSubmissionService.ReviewCommand toCommand() {
            return new HomeworkSubmissionService.ReviewCommand(manualScore, finalScore, comment);
        }
    }
}
