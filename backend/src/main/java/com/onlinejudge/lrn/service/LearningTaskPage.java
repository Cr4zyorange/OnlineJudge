package com.onlinejudge.lrn.service;

import java.util.List;

public record LearningTaskPage(
        List<LearningTaskSummary> records,
        long total,
        int page,
        int size
) {
}
