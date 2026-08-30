package com.onlinejudge.common.reliability;

import com.onlinejudge.hwk.repository.AssessmentEventOutboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Structured metrics source; dashboards can poll it without reading other service tables. */
@Service
public class ReliabilityMetricsService {
    private final AssessmentEventOutboxRepository assessmentOutbox;
    private final LearningReliabilityRepository learningReliability;

    public ReliabilityMetricsService(
            AssessmentEventOutboxRepository assessmentOutbox,
            LearningReliabilityRepository learningReliability
    ) {
        this.assessmentOutbox = assessmentOutbox;
        this.learningReliability = learningReliability;
    }

    public ReliabilityMetricsSnapshot snapshot() {
        long pending = assessmentOutbox.countByStatus("PENDING");
        long retrying = assessmentOutbox.countByStatus("RETRY");
        var oldestAssessment = assessmentOutbox.oldestAutomaticallyDeliverable();
        var oldestDeadLetter = learningReliability.oldestUnreplayedDeadLetter();
        var oldestDeferred = learningReliability.oldestUnresolvedDeferred();
        return new ReliabilityMetricsSnapshot(
                pending + retrying,
                pending,
                retrying,
                assessmentOutbox.countByStatus("FAILED"),
                oldestAssessment.map(AssessmentEventOutboxRepository.OutstandingEvent::eventId).orElse(null),
                oldestAssessment.map(AssessmentEventOutboxRepository.OutstandingEvent::correlationId).orElse(null),
                oldestAssessment.map(event -> Math.max(0, Duration.between(event.createdAt(), Instant.now()).toSeconds()))
                        .orElse(null),
                learningReliability.deadLetterCount(),
                oldestDeadLetter.map(LearningReliabilityRepository.DeadLetterObservation::eventId).orElse(null),
                oldestDeadLetter.map(LearningReliabilityRepository.DeadLetterObservation::correlationId).orElse(null),
                learningReliability.deferredCount(),
                oldestDeferred.map(LearningReliabilityRepository.DeferredObservation::eventId).orElse(null),
                oldestDeferred.map(LearningReliabilityRepository.DeferredObservation::correlationId).orElse(null)
        );
    }
}
