package com.onlinejudge.grd.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeRuleValidationResult;
import com.onlinejudge.grd.service.GradeItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class GradeItemController {
    private final GradeItemService gradeItemService;

    public GradeItemController(GradeItemService gradeItemService) {
        this.gradeItemService = gradeItemService;
    }

    @PostMapping("/courses/{courseId}/grade-items")
    public ResponseEntity<ApiResponse<GradeItem>> createGradeItem(
            @PathVariable long courseId,
            CurrentUser currentUser,
            @Valid @RequestBody CreateGradeItemRequest request
    ) {
        requireTeacher(currentUser);
        GradeItem item = gradeItemService.createGradeItem(courseId, currentUser.id(), request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(item));
    }

    @GetMapping("/courses/{courseId}/grade-items")
    public ApiResponse<List<GradeItem>> listGradeItems(
            @PathVariable long courseId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeItemService.listGradeItems(courseId, currentUser.id()));
    }

    @PutMapping("/grade-items/{gradeItemId}")
    public ApiResponse<GradeItem> updateGradeItem(
            @PathVariable long gradeItemId,
            CurrentUser currentUser,
            @Valid @RequestBody UpdateGradeItemRequest request
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeItemService.updateGradeItem(gradeItemId, currentUser.id(), request.toCommand()));
    }

    @DeleteMapping("/grade-items/{gradeItemId}")
    public ApiResponse<GradeItem> deleteGradeItem(
            @PathVariable long gradeItemId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(gradeItemService.deleteGradeItem(gradeItemId, currentUser.id()));
    }

    @PostMapping("/courses/{courseId}/grade-rules/validate")
    public ApiResponse<GradeRuleValidationResult> validateGradeRules(
            @PathVariable long courseId,
            CurrentUser currentUser,
            @Valid @RequestBody(required = false) GradeRuleValidationRequest request
    ) {
        requireTeacher(currentUser);
        if (request == null) {
            return ApiResponse.ok(gradeItemService.validateGradeRules(courseId, currentUser.id()));
        }
        return ApiResponse.ok(gradeItemService.validateGradeRules(courseId, currentUser.id(), request.toCommands()));
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无课程成绩管理权限");
        }
    }
}
