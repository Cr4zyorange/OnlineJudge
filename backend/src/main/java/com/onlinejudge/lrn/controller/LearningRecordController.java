package com.onlinejudge.lrn.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.lrn.service.LearningRecordItem;
import com.onlinejudge.lrn.service.LearningRecordRequest;
import com.onlinejudge.lrn.service.LearningRecordService;
import com.onlinejudge.lrn.service.LearningStatisticsOverview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningRecordController {
    private final LearningRecordService learningRecordService;

    public LearningRecordController(LearningRecordService learningRecordService) {
        this.learningRecordService = learningRecordService;
    }

    @GetMapping("/statistics")
    public ApiResponse<LearningStatisticsOverview> getStatistics(
            CurrentUser currentUser,
            @RequestParam(required = false) Long courseId
    ) {
        return ApiResponse.ok(learningRecordService.getStatistics(currentUser.id(), courseId));
    }

    @PostMapping("/records")
    public ApiResponse<LearningRecordItem> reportRecord(
            CurrentUser currentUser,
            @RequestBody LearningRecordRequest request
    ) {
        return ApiResponse.ok(learningRecordService.reportRecord(currentUser.id(), request));
    }
}
