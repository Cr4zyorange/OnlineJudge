package com.onlinejudge.assessmentservice.model;

import java.time.Instant;

public record EvaluationTask(String id, String submissionId, String sourceType, String sourceId,
                             String courseId, String studentId, TaskState state, long generation,
                             String leaseOwner, Instant leaseUntil, int attempt, String resultStatus,
                             String originRequestId) { }
