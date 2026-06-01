package com.onlinejudge.lrn.service;

import java.util.List;

public record LearningCourseProgressAggregate(
        long courseId,
        String courseName,
        int studentCount,
        int averageProgressPercent,
        List<LearningStudentProgressSummary> students
) {
}
