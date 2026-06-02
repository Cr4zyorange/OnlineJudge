package com.onlinejudge.crs.domain.dto;

public record CourseRecentTaskResponse(
        long taskId,
        String taskType,
        String title,
        long courseId,
        String courseName,
        String deadline,
        int progress,
        String status,
        String actionUrl
) {
}
