package com.onlinejudge.grd.service;

public record GradeSyncResult(
        long calculationBatchId,
        int affectedItemCount,
        int affectedStudentCount,
        int syncedCount,
        int missingCount,
        int ungradedCount
) {
}
