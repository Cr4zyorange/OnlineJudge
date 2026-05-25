package com.onlinejudge.common.evaluation;

import java.time.LocalDateTime;
import java.util.Map;

public record EvaluationTask(
        String taskId,
        String module,
        long courseId,
        long sourceId,
        long submissionId,
        long studentId,
        String language,
        String sourceCode,
        Map<String, String> options,
        LocalDateTime submittedAt
) {
}
