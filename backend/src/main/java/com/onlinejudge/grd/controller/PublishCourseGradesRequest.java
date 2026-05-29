package com.onlinejudge.grd.controller;

import java.util.List;

public record PublishCourseGradesRequest(
        String publishScope,
        List<Long> studentIds,
        List<Long> gradeItemIds
) {
}
