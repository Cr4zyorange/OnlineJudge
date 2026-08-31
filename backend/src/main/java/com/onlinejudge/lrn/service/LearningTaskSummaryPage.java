package com.onlinejudge.lrn.service;

import java.util.List;

/**
 * Bounded internal page shape for the Course -> LRN recent-task summary
 * contract.  {@code items} mirrors the v2 RecentTaskPage field names so the
 * Course client can map them directly onto its home-summary DTO.
 */
public record LearningTaskSummaryPage(
        List<LearningTaskSummary> items,
        long total,
        int page,
        int size
) {
}
