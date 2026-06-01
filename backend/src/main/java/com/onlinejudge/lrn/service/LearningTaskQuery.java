package com.onlinejudge.lrn.service;

public record LearningTaskQuery(
        String taskType,
        String status,
        Long courseId,
        String sortBy,
        String order,
        Integer page,
        Integer size
) {
}
