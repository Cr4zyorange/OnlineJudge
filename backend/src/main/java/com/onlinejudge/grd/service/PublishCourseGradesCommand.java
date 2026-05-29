package com.onlinejudge.grd.service;

import java.util.List;

public record PublishCourseGradesCommand(
        String publishScope,
        List<Long> studentIds,
        List<Long> gradeItemIds
) {
}
