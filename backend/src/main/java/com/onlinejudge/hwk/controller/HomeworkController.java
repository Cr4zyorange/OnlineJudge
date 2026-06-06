package com.onlinejudge.hwk.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmissionSearchCriteria;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.service.HomeworkService;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/homeworks")
public class HomeworkController {
    private final HomeworkService homeworkService;
    private final HomeworkSubmissionService homeworkSubmissionService;

    public HomeworkController(HomeworkService homeworkService, HomeworkSubmissionService homeworkSubmissionService) {
        this.homeworkService = homeworkService;
        this.homeworkSubmissionService = homeworkSubmissionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HomeworkResponse>> create(
            CurrentUser currentUser,
            @Valid @RequestBody HomeworkRequest request
    ) {
        HomeworkResponse response = HomeworkResponse.fromTeacherView(homeworkService.create(currentUser.id(), request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/{homeworkId}")
    public ApiResponse<HomeworkResponse> update(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @Valid @RequestBody HomeworkRequest request
    ) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.update(homeworkId, currentUser.id(), request.toCommand())));
    }

    @GetMapping
    public ApiResponse<PageResponse<HomeworkResponse>> list(
            CurrentUser currentUser,
            @RequestParam long courseId,
            @RequestParam(required = false) HomeworkStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<HomeworkResponse> response = mapPage(homeworkService.list(currentUser.id(), courseId, status, keyword, page, size));
        return ApiResponse.ok(response);
    }

    @GetMapping("/{homeworkId}")
    public ApiResponse<HomeworkResponse> detail(@PathVariable long homeworkId, CurrentUser currentUser) {
        com.onlinejudge.hwk.domain.Homework homework = homeworkService.detail(homeworkId, currentUser.id());
        if (homeworkService.canManageCourse(homework.courseId(), currentUser.id())) {
            return ApiResponse.ok(HomeworkResponse.fromTeacherView(homework));
        }
        return ApiResponse.ok(HomeworkResponse.fromStudentView(homework));
    }

    @GetMapping("/{homeworkId}/my-submissions")
    public ApiResponse<List<HomeworkSubmissionResponse>> mySubmissions(
            @PathVariable long homeworkId,
            CurrentUser currentUser
    ) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new com.onlinejudge.hwk.service.HomeworkApiException(
                    "HWK_4031",
                    "only students can view own homework submissions",
                    HttpStatus.FORBIDDEN
            );
        }
        com.onlinejudge.hwk.service.HomeworkSubmissionService.SubmissionHistory history =
                homeworkSubmissionService.listMine(homeworkId, currentUser.id());
        return ApiResponse.ok(history.submissions()
                .stream()
                .map(submission -> HomeworkSubmissionResponse.fromStudentView(history.homework(), submission))
                .toList());
    }

    @GetMapping("/{homeworkId}/submissions")
    public ApiResponse<PageResponse<HomeworkSubmissionResponse>> submissions(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @RequestParam(required = false) String studentKeyword,
            @RequestParam(required = false) HomeworkSubmitStatus submitStatus,
            @RequestParam(required = false) EvaluationStatus evaluationStatus,
            @RequestParam(required = false) HomeworkReviewStatus reviewStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<com.onlinejudge.hwk.domain.HomeworkSubmission> submissions =
                homeworkSubmissionService.listForManager(
                        homeworkId,
                        currentUser.id(),
                        HomeworkSubmissionSearchCriteria.of(studentKeyword, submitStatus, evaluationStatus, reviewStatus),
                        page,
                        size
                );
        return ApiResponse.ok(new PageResponse<>(
                submissions.list().stream().map(HomeworkSubmissionResponse::fromTeacherView).toList(),
                submissions.total(),
                submissions.page(),
                submissions.size()
        ));
    }

    @PostMapping("/{homeworkId}/submissions")
    public ResponseEntity<ApiResponse<HomeworkSubmissionResponse>> submit(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @RequestBody(required = false) HomeworkSubmissionRequest request
    ) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new com.onlinejudge.hwk.service.HomeworkApiException(
                    "HWK_4031",
                    "only students can submit homework",
                    HttpStatus.FORBIDDEN
            );
        }
        com.onlinejudge.hwk.service.HomeworkSubmissionService.SubmittedHomeworkSubmission submitted =
                homeworkSubmissionService.submit(
                        homeworkId,
                        currentUser.id(),
                        request == null ? null : request.toCommand()
                );
        HomeworkSubmissionResponse response = HomeworkSubmissionResponse.fromStudentView(
                submitted.homework(),
                submitted.submission()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/{homeworkId}/questions")
    public ApiResponse<HomeworkResponse> saveQuestions(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @Valid @RequestBody List<HomeworkQuestionPayload> questions
    ) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.saveQuestions(
                homeworkId,
                currentUser.id(),
                questions.stream().map(HomeworkQuestionPayload::toDomain).toList()
        )));
    }

    @PutMapping("/{homeworkId}/test-cases")
    public ApiResponse<HomeworkResponse> saveTestCases(
            @PathVariable long homeworkId,
            CurrentUser currentUser,
            @Valid @RequestBody List<HomeworkTestCasePayload> testCases
    ) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.saveTestCases(
                homeworkId,
                currentUser.id(),
                testCases.stream().map(HomeworkTestCasePayload::toDomain).toList()
        )));
    }

    @GetMapping("/{homeworkId}/test-cases")
    public ApiResponse<List<HomeworkTestCaseResponse>> testCases(
            @PathVariable long homeworkId,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(homeworkService.testCases(homeworkId, currentUser.id())
                .stream()
                .map(HomeworkTestCaseResponse::from)
                .toList());
    }

    @PutMapping("/{homeworkId}/publish")
    public ApiResponse<HomeworkResponse> publish(@PathVariable long homeworkId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.publish(homeworkId, currentUser.id())));
    }

    @PutMapping("/{homeworkId}/close")
    public ApiResponse<HomeworkResponse> close(@PathVariable long homeworkId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.close(homeworkId, currentUser.id())));
    }

    @PutMapping("/{homeworkId}/scores/publish")
    public ApiResponse<HomeworkResponse> publishScores(@PathVariable long homeworkId, CurrentUser currentUser) {
        return ApiResponse.ok(HomeworkResponse.fromTeacherView(homeworkService.publishScores(homeworkId, currentUser.id())));
    }

    @GetMapping("/{homeworkId}/statistics")
    public ApiResponse<HomeworkService.HomeworkStatistics> statistics(
            @PathVariable long homeworkId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            CurrentUser currentUser
    ) {
        return ApiResponse.ok(homeworkService.statistics(homeworkId, currentUser.id(), page, size));
    }

    private PageResponse<HomeworkResponse> mapPage(PageResponse<com.onlinejudge.hwk.domain.Homework> page) {
        return new PageResponse<>(page.list().stream().map(HomeworkResponse::summary).toList(), page.total(), page.page(), page.size());
    }
}
