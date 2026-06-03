package com.onlinejudge.grd.service;

import java.util.List;

public record GradeReviewRequestPage(
        List<GradeReviewRequestView> records,
        int total,
        int page,
        int size
) {
}
