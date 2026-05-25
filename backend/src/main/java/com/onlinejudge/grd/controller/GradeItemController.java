package com.onlinejudge.grd.controller;

import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeRuleValidationResult;
import com.onlinejudge.grd.service.GradeItemPermissionException;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody CreateGradeItemRequest request
    ) {
        requireTeacher(userRole);
        GradeItem item = gradeItemService.createGradeItem(courseId, userId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(item));
    }

    @GetMapping("/courses/{courseId}/grade-items")
    public ApiResponse<List<GradeItem>> listGradeItems(
            @PathVariable long courseId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader("X-User-Role") String userRole
    ) {
        requireTeacher(userRole);
        return ApiResponse.ok(gradeItemService.listGradeItems(courseId, userId));
    }

    @PutMapping("/grade-items/{gradeItemId}")
    public ApiResponse<GradeItem> updateGradeItem(
            @PathVariable long gradeItemId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody UpdateGradeItemRequest request
    ) {
        requireTeacher(userRole);
        return ApiResponse.ok(gradeItemService.updateGradeItem(gradeItemId, userId, request.toCommand()));
    }

    @DeleteMapping("/grade-items/{gradeItemId}")
    public ApiResponse<GradeItem> deleteGradeItem(
            @PathVariable long gradeItemId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader("X-User-Role") String userRole
    ) {
        requireTeacher(userRole);
        return ApiResponse.ok(gradeItemService.deleteGradeItem(gradeItemId, userId));
    }

    @PostMapping("/courses/{courseId}/grade-rules/validate")
    public ApiResponse<GradeRuleValidationResult> validateGradeRules(
            @PathVariable long courseId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody(required = false) GradeRuleValidationRequest request
    ) {
        requireTeacher(userRole);
        if (request == null) {
            return ApiResponse.ok(gradeItemService.validateGradeRules(courseId, userId));
        }
        return ApiResponse.ok(gradeItemService.validateGradeRules(courseId, userId, request.toCommands()));
    }

    private void requireTeacher(String userRole) {
        if (!"TEACHER".equals(userRole) && !"ADMIN".equals(userRole)) {
            throw new GradeItemPermissionException("教师无课程成绩管理权限");
        }
    }
}
