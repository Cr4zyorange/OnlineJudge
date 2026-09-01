package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnRecordService;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LrnRecordController {
    private final LrnRecordService records;

    public LrnRecordController(LrnRecordService records) { this.records = records; }

    @GetMapping("/statistics")
    public ApiResponse<LrnRecordService.LearningStatisticsOverview> statistics(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(records.statistics(user.id(), courseId));
    }

    @PostMapping("/records")
    public ApiResponse<LrnRecordService.LearningRecordItem> report(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestBody LrnRecordService.LearningRecordRequest request) {
        return ApiResponse.ok(records.report(user.id(), request));
    }
}
