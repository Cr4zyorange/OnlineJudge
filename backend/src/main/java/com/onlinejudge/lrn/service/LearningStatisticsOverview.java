package com.onlinejudge.lrn.service;

import java.util.List;

public record LearningStatisticsOverview(
        LearningStatisticsSummary summary,
        List<LearningTrendPoint> trends,
        List<LearningRecordItem> recentRecords
) {
}
