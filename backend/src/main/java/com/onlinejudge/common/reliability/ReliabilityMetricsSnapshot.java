package com.onlinejudge.common.reliability;

public record ReliabilityMetricsSnapshot(
        long assessmentBacklog,
        long assessmentPending,
        long assessmentRetrying,
        long assessmentFailed,
        String oldestAssessmentEventId,
        String oldestAssessmentCorrelationId,
        Long oldestAssessmentAgeSeconds,
        long learningDeadLetters,
        String oldestLearningDeadLetterEventId,
        String oldestLearningDeadLetterCorrelationId
) {
}
