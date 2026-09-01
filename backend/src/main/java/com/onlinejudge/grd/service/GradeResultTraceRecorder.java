package com.onlinejudge.grd.service;

/** Optional persistence boundary used by the independently deployed Grade service. */
@FunctionalInterface
public interface GradeResultTraceRecorder {
    void record(long courseId, long calculationBatchId);
}
