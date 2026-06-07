package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.LabStatisticsView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LabStatisticsResponse(
        long labId,
        long courseId,
        int totalStudentCount,
        int submittedCount,
        int unsubmittedCount,
        int evaluatedCount,
        BigDecimal submissionRate,
        BigDecimal evaluationCompletionRate,
        BigDecimal averageScore,
        int lateSubmissionCount,
        List<Long> unsubmittedStudentIds,
        Map<String, Integer> scoreDistribution,
        LocalDateTime generatedAt
) {
    public static LabStatisticsResponse from(LabStatisticsView view) {
        return new LabStatisticsResponse(
                view.labId(),
                view.courseId(),
                view.totalStudentCount(),
                view.submittedCount(),
                view.unsubmittedCount(),
                view.evaluatedCount(),
                view.submissionRate(),
                view.evaluationCompletionRate(),
                view.averageScore(),
                view.lateSubmissionCount(),
                view.unsubmittedStudentIds(),
                view.scoreDistribution(),
                view.generatedAt()
        );
    }
}
