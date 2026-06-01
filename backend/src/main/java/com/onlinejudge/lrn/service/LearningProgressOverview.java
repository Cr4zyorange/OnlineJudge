package com.onlinejudge.lrn.service;

import java.util.List;

public record LearningProgressOverview(
        List<LearningCourseProgress> courses,
        int total
) {
}
